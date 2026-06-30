package com.ledgerone.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerone.market.quotes")
public record MarketQuoteProperties(boolean enabled, int cacheSeconds, String yahooChartUrl) {
    public MarketQuoteProperties {
        cacheSeconds = cacheSeconds <= 0 ? 60 : cacheSeconds;
        yahooChartUrl = yahooChartUrl == null || yahooChartUrl.isBlank()
                ? "https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1m&range=1d"
                : yahooChartUrl;
    }
}
