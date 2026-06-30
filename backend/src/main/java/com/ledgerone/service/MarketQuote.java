package com.ledgerone.service;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(String symbol, BigDecimal price, Instant observedAt, String source) {}
