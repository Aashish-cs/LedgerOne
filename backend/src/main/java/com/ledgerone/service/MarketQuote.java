package com.ledgerone.service;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(String symbol, BigDecimal price, Instant observedAt, String source, String companyName, String sector) {
    public MarketQuote(String symbol, BigDecimal price, Instant observedAt, String source) {
        this(symbol, price, observedAt, source, null, null);
    }
}
