package com.ledgerone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerone.exception.BadRequestException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockQuoteService {
    private final MarketQuoteProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public boolean livePricesEnabled() {
        return properties.enabled();
    }

    public MarketQuote quote(String symbol) {
        if (!livePricesEnabled()) {
            throw new BadRequestException("Live market prices are disabled");
        }
        String normalized = symbol.toUpperCase(Locale.US);
        CachedQuote cached = cache.get(normalized);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.quote();
        }
        MarketQuote quote = fetchQuote(normalized);
        cache.put(normalized, new CachedQuote(quote, now.plusSeconds(properties.cacheSeconds())));
        return quote;
    }

    private MarketQuote fetchQuote(String symbol) {
        BadRequestException lastFailure = null;
        for (String urlTemplate : quoteUrlTemplates()) {
            try {
                return fetchQuoteFromTemplate(symbol, urlTemplate);
            } catch (BadRequestException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new BadRequestException("Live price unavailable for " + symbol);
    }

    private LinkedHashSet<String> quoteUrlTemplates() {
        LinkedHashSet<String> templates = new LinkedHashSet<>();
        templates.add(properties.quoteUrlTemplate());
        templates.add("https://api.nasdaq.com/api/quote/{symbol}/info?assetclass=stocks");
        templates.add("https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1m&range=1d");
        return templates;
    }

    private MarketQuote fetchQuoteFromTemplate(String symbol, String urlTemplate) {
        String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        URI uri = URI.create(urlTemplate.replace("{symbol}", encoded));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("User-Agent", "Mozilla/5.0 (compatible; LedgerOne/1.0)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("Live price unavailable for " + symbol);
            }
            return parseQuote(symbol, response.body());
        } catch (IOException exception) {
            throw new BadRequestException("Live price unavailable for " + symbol);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Live price unavailable for " + symbol);
        }
    }

    private MarketQuote parseQuote(String symbol, String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        MarketQuote nasdaqQuote = parseNasdaqQuote(symbol, root);
        if (nasdaqQuote != null) {
            return nasdaqQuote;
        }
        MarketQuote yahooQuote = parseYahooQuote(symbol, root);
        if (yahooQuote != null) {
            return yahooQuote;
        }
        throw new BadRequestException("Live price unavailable for " + symbol);
    }

    private MarketQuote parseNasdaqQuote(String symbol, JsonNode root) {
        JsonNode primaryData = root.path("data").path("primaryData");
        JsonNode lastSalePrice = primaryData.path("lastSalePrice");
        if (!lastSalePrice.isTextual()) {
            return null;
        }
        String normalizedPrice = lastSalePrice.asText().replace("$", "").replace(",", "").trim();
        if (normalizedPrice.isBlank()) {
            return null;
        }
        try {
            return new MarketQuote(symbol, Money.money(new BigDecimal(normalizedPrice)), Instant.now(), "Nasdaq public quote");
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private MarketQuote parseYahooQuote(String symbol, JsonNode root) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode meta = result.get(0).path("meta");
        JsonNode priceNode = meta.path("regularMarketPrice");
        if (!priceNode.isNumber()) {
            return null;
        }
        BigDecimal price = Money.money(priceNode.decimalValue());
        long marketTime = meta.path("regularMarketTime").asLong(Instant.now().getEpochSecond());
        return new MarketQuote(symbol, price, Instant.ofEpochSecond(marketTime), "Yahoo Finance chart");
    }

    private record CachedQuote(MarketQuote quote, Instant expiresAt) {}
}
