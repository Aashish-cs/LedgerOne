package com.ledgerone.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerone.market.quotes")
public record MarketQuoteProperties(
        boolean enabled,
        int cacheSeconds,
        String quoteUrlTemplate,
        String finnhubApiKey,
        String finnhubBaseUrl) {
    public MarketQuoteProperties {
        cacheSeconds = cacheSeconds <= 0 ? 60 : cacheSeconds;
        quoteUrlTemplate = quoteUrlTemplate == null || quoteUrlTemplate.isBlank()
                ? "https://api.nasdaq.com/api/quote/{symbol}/info?assetclass=stocks"
                : quoteUrlTemplate;
        finnhubApiKey = finnhubApiKey == null ? "" : finnhubApiKey.trim();
        finnhubBaseUrl = finnhubBaseUrl == null || finnhubBaseUrl.isBlank()
                ? "https://finnhub.io/api/v1"
                : finnhubBaseUrl.replaceAll("/+$", "");
    }
}
