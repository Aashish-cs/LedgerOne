package com.ledgerone.dto;

import com.ledgerone.entity.OrderSide;
import com.ledgerone.entity.OrderStatus;
import com.ledgerone.entity.OrderType;
import com.ledgerone.entity.TransactionAction;
import com.ledgerone.validation.ValidTicker;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TradingDtos {
    private TradingDtos() {}

    public record OrderRequest(
            @NotNull UUID portfolioId,
            @ValidTicker String symbol,
            @NotNull OrderSide side,
            @NotNull OrderType type,
            @NotNull @DecimalMin(value = "0.000001") BigDecimal quantity,
            @DecimalMin(value = "0.01") BigDecimal limitPrice,
            @NotBlank @Size(max = 80) String clientOrderId) {}

    public record OrderResponse(
            UUID id,
            String clientOrderId,
            UUID portfolioId,
            String portfolioName,
            String symbol,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            BigDecimal quantity,
            BigDecimal limitPrice,
            BigDecimal executionPrice,
            BigDecimal fees,
            String rejectionReason,
            Instant createdAt,
            Instant filledAt) {}

    public record TransactionResponse(
            UUID id,
            UUID orderId,
            String symbol,
            TransactionAction action,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal fees,
            Instant createdAt) {}
}
