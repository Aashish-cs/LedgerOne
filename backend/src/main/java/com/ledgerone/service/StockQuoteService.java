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
        MarketQuote quote = fetchYahooQuote(normalized);
        cache.put(normalized, new CachedQuote(quote, now.plusSeconds(properties.cacheSeconds())));
        return quote;
    }

    private MarketQuote fetchYahooQuote(String symbol) {
        String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        URI uri = URI.create(properties.yahooChartUrl().replace("{symbol}", encoded));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "LedgerOne/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("Live price unavailable for " + symbol);
            }
            return parseYahooQuote(symbol, response.body());
        } catch (IOException exception) {
            throw new BadRequestException("Live price unavailable for " + symbol);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Live price unavailable for " + symbol);
        }
    }

    private MarketQuote parseYahooQuote(String symbol, String body) throws IOException {
        JsonNode result = objectMapper.readTree(body).path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new BadRequestException("Live price unavailable for " + symbol);
        }
        JsonNode meta = result.get(0).path("meta");
        JsonNode priceNode = meta.path("regularMarketPrice");
        if (!priceNode.isNumber()) {
            throw new BadRequestException("Live price unavailable for " + symbol);
        }
        BigDecimal price = Money.money(priceNode.decimalValue());
        long marketTime = meta.path("regularMarketTime").asLong(Instant.now().getEpochSecond());
        return new MarketQuote(symbol, price, Instant.ofEpochSecond(marketTime), "Yahoo Finance chart");
    }

    private record CachedQuote(MarketQuote quote, Instant expiresAt) {}
}
