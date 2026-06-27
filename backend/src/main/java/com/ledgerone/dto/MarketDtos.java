package com.ledgerone.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MarketDtos {
    private MarketDtos() {}

    public record StockResponse(UUID id, String symbol, String companyName, String sector, BigDecimal lastPrice, Instant updatedAt) {}

    public record PricePoint(String symbol, BigDecimal price, Instant observedAt) {}
}
