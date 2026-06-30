package com.ledgerone.service;

import com.ledgerone.dto.MarketDtos;
import com.ledgerone.entity.PriceHistory;
import com.ledgerone.entity.Stock;
import com.ledgerone.exception.BadRequestException;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.mapper.StockMapper;
import com.ledgerone.repository.PriceHistoryRepository;
import com.ledgerone.repository.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketDataService {
    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final StockMapper stockMapper;
    private final StockQuoteService stockQuoteService;
    private final Random random = new Random();

    @Transactional
    public List<MarketDtos.StockResponse> listStocks() {
        return stockRepository.findAll().stream()
                .sorted(Comparator.comparing(Stock::getSymbol))
                .map(this::refreshLivePrice)
                .map(stockMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<MarketDtos.StockResponse> searchStocks(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 1) {
            return List.of();
        }
        List<MarketDtos.StockResponse> liveResults = new ArrayList<>();
        for (MarketSearchResult result : stockQuoteService.search(normalizedQuery, 8)) {
            try {
                liveResults.add(stockMapper.toResponse(findOrCreateStock(result.symbol(), result)));
            } catch (RuntimeException exception) {
                // Search providers can return stale or unsupported symbols; keep the usable live results.
            }
        }
        if (!liveResults.isEmpty()) {
            return liveResults;
        }
        return stockRepository
                .findTop8BySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(normalizedQuery, normalizedQuery)
                .stream()
                .map(this::refreshLivePrice)
                .map(stockMapper::toResponse)
                .toList();
    }

    @Transactional
    public MarketDtos.StockResponse quote(String symbol) {
        return stockMapper.toResponse(findOrCreateStock(symbol, null));
    }

    @Transactional(readOnly = true)
    public Stock findStock(String symbol) {
        return stockRepository
                .findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));
    }

    @Transactional
    public Stock findTradableStock(String symbol) {
        return findOrCreateStock(symbol, null);
    }

    @Transactional(readOnly = true)
    public List<MarketDtos.PricePoint> history(String symbol, int days) {
        Stock stock = findStock(symbol);
        Instant floor = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return priceHistoryRepository.findByStockAndObservedAtAfterOrderByObservedAtAsc(stock, floor).stream()
                .map(point -> new MarketDtos.PricePoint(stock.getSymbol(), point.getPrice(), point.getObservedAt()))
                .toList();
    }

    @Transactional
    public void simulateTick() {
        if (stockQuoteService.livePricesEnabled()) {
            stockRepository.findAll().forEach(this::refreshLivePrice);
            return;
        }
        stockRepository.findAll().forEach(stock -> {
            BigDecimal movePercent = BigDecimal.valueOf((random.nextDouble() - 0.48) / 70.0);
            BigDecimal nextPrice = stock.getLastPrice()
                    .multiply(BigDecimal.ONE.add(movePercent))
                    .max(BigDecimal.ONE)
                    .setScale(4, RoundingMode.HALF_UP);
            stock.setLastPrice(nextPrice);
            stockRepository.save(stock);

            PriceHistory history = new PriceHistory();
            history.setStock(stock);
            history.setPrice(nextPrice);
            history.setObservedAt(Instant.now());
            priceHistoryRepository.save(history);
        });
    }

    private Stock refreshLivePrice(Stock stock) {
        if (!stockQuoteService.livePricesEnabled()) {
            return stock;
        }
        MarketQuote quote = stockQuoteService.quote(stock.getSymbol());
        stock.setLastPrice(quote.price());
        stockRepository.save(stock);

        PriceHistory history = new PriceHistory();
        history.setStock(stock);
        history.setPrice(quote.price());
        history.setObservedAt(quote.observedAt());
        priceHistoryRepository.save(history);
        return stock;
    }

    private Stock findOrCreateStock(String symbol, MarketSearchResult metadata) {
        String normalizedSymbol = normalizeSymbol(symbol);
        if (!normalizedSymbol.matches("[A-Z][A-Z0-9.-]{0,9}")) {
            throw new BadRequestException("Ticker must be 1 to 10 letters, numbers, dots, or hyphens");
        }
        return stockRepository
                .findBySymbolIgnoreCase(normalizedSymbol)
                .map(stock -> updateMetadata(refreshLivePrice(stock), metadata))
                .orElseGet(() -> createLiveStock(normalizedSymbol, metadata));
    }

    private Stock createLiveStock(String symbol, MarketSearchResult metadata) {
        MarketQuote quote = stockQuoteService.quote(symbol);
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setCompanyName(limit(firstNonBlank(
                quote.companyName(),
                metadata == null ? null : metadata.companyName(),
                symbol), 160));
        stock.setSector(limit(firstNonBlank(
                metadata == null ? null : metadata.sector(),
                quote.sector(),
                "Equity"), 80));
        stock.setLastPrice(quote.price());
        Stock saved = stockRepository.save(stock);
        saveHistory(saved, quote.price(), quote.observedAt());
        return saved;
    }

    private Stock updateMetadata(Stock stock, MarketSearchResult metadata) {
        if (metadata != null && metadata.companyName() != null && !metadata.companyName().isBlank()) {
            stock.setCompanyName(limit(metadata.companyName(), 160));
        }
        if (metadata != null && metadata.sector() != null && !metadata.sector().isBlank()) {
            stock.setSector(limit(metadata.sector(), 80));
        }
        return stock;
    }

    private void saveHistory(Stock stock, BigDecimal price, Instant observedAt) {
        PriceHistory history = new PriceHistory();
        history.setStock(stock);
        history.setPrice(price);
        history.setObservedAt(observedAt);
        priceHistoryRepository.save(history);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().replace("/", ".").toUpperCase(Locale.US);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        String normalized = firstNonBlank(value, "Unknown");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
