package com.ledgerone.dto;

import com.ledgerone.validation.ValidTicker;
import java.time.Instant;
import java.util.UUID;

public final class WatchlistDtos {
    private WatchlistDtos() {}

    public record WatchlistRequest(@ValidTicker String symbol) {}

    public record WatchlistResponse(UUID id, MarketDtos.StockResponse stock, Instant createdAt) {}
}
