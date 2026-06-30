package com.ledgerone.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerone.market.quotes")
public record MarketQuoteProperties(boolean enabled, int cacheSeconds, String quoteUrlTemplate) {
    public MarketQuoteProperties {
        cacheSeconds = cacheSeconds <= 0 ? 60 : cacheSeconds;
        quoteUrlTemplate = quoteUrlTemplate == null || quoteUrlTemplate.isBlank()
                ? "https://api.nasdaq.com/api/quote/{symbol}/info?assetclass=stocks"
                : quoteUrlTemplate;
    }
}
