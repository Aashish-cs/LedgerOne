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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockQuoteService {
    private final MarketQuoteProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedProfile> profileCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public boolean livePricesEnabled() {
        return properties.enabled();
    }

    public MarketQuote quote(String symbol) {
        if (!livePricesEnabled()) {
            throw new BadRequestException("Live market prices are disabled");
        }
        String normalized = normalizeSymbol(symbol);
        CachedQuote cached = cache.get(normalized);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.quote();
        }
        MarketQuote quote = fetchQuote(normalized);
        cache.put(normalized, new CachedQuote(quote, now.plusSeconds(properties.cacheSeconds())));
        return quote;
    }

    public List<MarketSearchResult> search(String query, int limit) {
        if (!livePricesEnabled()) {
            return List.of();
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 1) {
            return List.of();
        }
        int boundedLimit = Math.max(1, Math.min(limit, 10));
        List<MarketSearchResult> finnhubResults = searchFinnhub(normalizedQuery);
        if (!finnhubResults.isEmpty()) {
            return finnhubResults.stream().limit(boundedLimit).toList();
        }
        Map<String, MarketSearchResult> results = new LinkedHashMap<>();
        searchNasdaq(normalizedQuery).forEach(result -> results.putIfAbsent(result.symbol(), result));
        searchYahoo(normalizedQuery).forEach(result -> results.putIfAbsent(result.symbol(), result));
        return results.values().stream().limit(boundedLimit).toList();
    }

    private MarketQuote fetchQuote(String symbol) {
        BadRequestException lastFailure = null;
        if (finnhubConfigured()) {
            try {
                return fetchFinnhubQuote(symbol);
            } catch (BadRequestException exception) {
                lastFailure = exception;
            }
        }
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

    private MarketQuote fetchFinnhubQuote(String symbol) {
        URI uri = finnhubUri("/quote", Map.of("symbol", symbol));
        try {
            JsonNode root = objectMapper.readTree(getJson(uri));
            JsonNode currentPrice = root.path("c");
            if (!currentPrice.isNumber() || currentPrice.decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Finnhub price unavailable for " + symbol);
            }
            long timestamp = root.path("t").asLong(Instant.now().getEpochSecond());
            Optional<FinnhubProfile> profile = fetchFinnhubProfile(symbol);
            return new MarketQuote(
                    symbol,
                    Money.money(currentPrice.decimalValue()),
                    timestamp > 0 ? Instant.ofEpochSecond(timestamp) : Instant.now(),
                    "Finnhub quote",
                    profile.map(FinnhubProfile::name).orElse(symbol),
                    profile.map(FinnhubProfile::industry).orElse("Equity"));
        } catch (IOException exception) {
            throw new BadRequestException("Finnhub price unavailable for " + symbol);
        }
    }

    private LinkedHashSet<String> quoteUrlTemplates() {
        LinkedHashSet<String> templates = new LinkedHashSet<>();
        templates.add(properties.quoteUrlTemplate());
        templates.add("https://api.nasdaq.com/api/quote/{symbol}/info?assetclass=stocks");
        templates.add("https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1m&range=1d");
        return templates;
    }

    private MarketQuote fetchQuoteFromTemplate(String symbol, String urlTemplate) {
        String encoded = encode(symbol);
        URI uri = URI.create(urlTemplate.replace("{symbol}", encoded));
        String body = getJson(uri);
        try {
            return parseQuote(symbol, body);
        } catch (IOException exception) {
            throw new BadRequestException("Live price unavailable for " + symbol);
        }
    }

    private List<MarketSearchResult> searchNasdaq(String query) {
        URI uri = URI.create("https://api.nasdaq.com/api/autocomplete/slookup/10?search=" + encode(query));
        try {
            JsonNode data = objectMapper.readTree(getJson(uri)).path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<MarketSearchResult> results = new ArrayList<>();
            for (JsonNode item : data) {
                String asset = item.path("asset").asText("");
                if (!asset.equalsIgnoreCase("STOCKS")) {
                    continue;
                }
                String symbol = normalizeSymbol(item.path("symbol").asText(""));
                if (symbol.isBlank()) {
                    continue;
                }
                String name = cleanCompanyName(item.path("name").asText(symbol));
                String sector = firstNonBlank(item.path("industry").asText(), "Equity");
                String exchange = firstNonBlank(item.path("exchange").asText(), item.path("mrktCategory").asText());
                results.add(new MarketSearchResult(symbol, name, sector, exchange));
            }
            return results;
        } catch (RuntimeException | IOException exception) {
            return List.of();
        }
    }

    private List<MarketSearchResult> searchFinnhub(String query) {
        if (!finnhubConfigured()) {
            return List.of();
        }
        URI uri = finnhubUri("/search", Map.of("q", query));
        try {
            JsonNode results = objectMapper.readTree(getJson(uri)).path("result");
            if (!results.isArray()) {
                return List.of();
            }
            List<MarketSearchResult> parsed = new ArrayList<>();
            for (JsonNode item : results) {
                String type = item.path("type").asText("");
                if (!type.isBlank() && !type.toLowerCase(Locale.US).contains("stock") && !type.equalsIgnoreCase("Equity")) {
                    continue;
                }
                String symbol = normalizeSymbol(firstNonBlank(item.path("symbol").asText(), item.path("displaySymbol").asText()));
                if (symbol.isBlank() || !symbol.matches("[A-Z][A-Z0-9.-]{0,9}")) {
                    continue;
                }
                String name = cleanCompanyName(firstNonBlank(item.path("description").asText(), symbol));
                parsed.add(new MarketSearchResult(symbol, name, firstNonBlank(type, "Equity"), "Finnhub"));
            }
            return parsed;
        } catch (RuntimeException | IOException exception) {
            return List.of();
        }
    }

    private List<MarketSearchResult> searchYahoo(String query) {
        URI uri = URI.create("https://query2.finance.yahoo.com/v1/finance/search?q=" + encode(query) + "&quotesCount=10&newsCount=0");
        try {
            JsonNode quotes = objectMapper.readTree(getJson(uri)).path("quotes");
            if (!quotes.isArray()) {
                return List.of();
            }
            List<MarketSearchResult> results = new ArrayList<>();
            for (JsonNode item : quotes) {
                String quoteType = item.path("quoteType").asText("");
                if (!quoteType.equalsIgnoreCase("EQUITY")) {
                    continue;
                }
                String symbol = normalizeSymbol(item.path("symbol").asText(""));
                if (symbol.isBlank()) {
                    continue;
                }
                String name = firstNonBlank(item.path("longname").asText(), item.path("shortname").asText(), symbol);
                String sector = firstNonBlank(item.path("sectorDisp").asText(), item.path("sector").asText(), "Equity");
                String exchange = firstNonBlank(item.path("exchDisp").asText(), item.path("exchange").asText());
                results.add(new MarketSearchResult(symbol, cleanCompanyName(name), sector, exchange));
            }
            return results;
        } catch (RuntimeException | IOException exception) {
            return List.of();
        }
    }

    private String getJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("User-Agent", "Mozilla/5.0 (compatible; LedgerOne/1.0)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new BadRequestException("Market data rate limit reached");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("Live market data unavailable");
            }
            return response.body();
        } catch (IOException exception) {
            throw new BadRequestException("Live market data unavailable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Live market data unavailable");
        }
    }

    private Optional<FinnhubProfile> fetchFinnhubProfile(String symbol) {
        if (!finnhubConfigured()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        CachedProfile cached = profileCache.get(symbol);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.profile();
        }
        URI uri = finnhubUri("/stock/profile2", Map.of("symbol", symbol));
        try {
            JsonNode root = objectMapper.readTree(getJson(uri));
            String name = cleanCompanyName(firstNonBlank(root.path("name").asText(), root.path("ticker").asText(), symbol));
            String industry = firstNonBlank(root.path("finnhubIndustry").asText(), "Equity");
            Optional<FinnhubProfile> profile = name.equals("Unknown")
                    ? Optional.empty()
                    : Optional.of(new FinnhubProfile(name, industry));
            profileCache.put(symbol, new CachedProfile(profile, now.plus(Duration.ofHours(6))));
            return profile;
        } catch (RuntimeException | IOException exception) {
            profileCache.put(symbol, new CachedProfile(Optional.empty(), now.plus(Duration.ofMinutes(15))));
            return Optional.empty();
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
            String companyName = cleanCompanyName(root.path("data").path("companyName").asText(symbol));
            String sector = firstNonBlank(root.path("data").path("stockType").asText(), root.path("data").path("assetClass").asText(), "Equity");
            return new MarketQuote(symbol, Money.money(new BigDecimal(normalizedPrice)), Instant.now(), "Nasdaq public quote", companyName, sector);
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
        String companyName = firstNonBlank(meta.path("longName").asText(), meta.path("shortName").asText(), symbol);
        String sector = firstNonBlank(meta.path("instrumentType").asText(), "Equity");
        return new MarketQuote(symbol, price, Instant.ofEpochSecond(marketTime), "Yahoo Finance chart", cleanCompanyName(companyName), sector);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().replace("/", ".").toUpperCase(Locale.US);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean finnhubConfigured() {
        return properties.finnhubApiKey() != null && !properties.finnhubApiKey().isBlank();
    }

    private URI finnhubUri(String path, Map<String, String> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((key, value) -> query.add(encode(key) + "=" + encode(value)));
        query.add("token=" + encode(properties.finnhubApiKey()));
        return URI.create(properties.finnhubBaseUrl() + path + "?" + query);
    }

    private String cleanCompanyName(String value) {
        return firstNonBlank(value, "Unknown")
                .replace("Class A Common Stock", "Class A")
                .replace("Class B Common Stock", "Class B")
                .replace("Common Stock", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record CachedQuote(MarketQuote quote, Instant expiresAt) {}

    private record CachedProfile(Optional<FinnhubProfile> profile, Instant expiresAt) {}

    private record FinnhubProfile(String name, String industry) {}
}
