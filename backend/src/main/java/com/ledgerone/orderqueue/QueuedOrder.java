package com.ledgerone.orderqueue;

import com.ledgerone.dto.TradingDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record QueuedOrder(@NotNull UUID userId, @Valid @NotNull TradingDtos.OrderRequest order) {}
