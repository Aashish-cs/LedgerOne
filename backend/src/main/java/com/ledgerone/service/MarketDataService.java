package com.ledgerone.service;

import com.ledgerone.dto.MarketDtos;
import com.ledgerone.entity.PriceHistory;
import com.ledgerone.entity.Stock;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.mapper.StockMapper;
import com.ledgerone.repository.PriceHistoryRepository;
import com.ledgerone.repository.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
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

    @Transactional(readOnly = true)
    public Stock findStock(String symbol) {
        return stockRepository
                .findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));
    }

    @Transactional
    public Stock findTradableStock(String symbol) {
        return refreshLivePrice(findStock(symbol));
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
}
