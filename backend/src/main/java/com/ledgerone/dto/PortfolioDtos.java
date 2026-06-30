package com.ledgerone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PortfolioDtos {
    private PortfolioDtos() {}

    public record PortfolioCreateRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotNull @Positive BigDecimal initialAllocation) {}

    public record PortfolioRenameRequest(@NotBlank @Size(min = 2, max = 120) String name) {}

    public record HoldingResponse(
            UUID id,
            String symbol,
            String companyName,
            String sector,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal marketPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedProfit,
            BigDecimal realizedProfit,
            BigDecimal allocationPercent) {}

    public record AllocationSlice(String label, BigDecimal value, BigDecimal percent) {}

    public record PortfolioResponse(
            UUID id,
            String name,
            BigDecimal cashBalance,
            BigDecimal marketValue,
            BigDecimal totalValue,
            BigDecimal realizedProfit,
            BigDecimal unrealizedProfit,
            List<HoldingResponse> holdings,
            List<AllocationSlice> allocation,
            Instant createdAt,
            Instant updatedAt) {}
}
