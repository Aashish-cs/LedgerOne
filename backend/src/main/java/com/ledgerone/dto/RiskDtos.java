package com.ledgerone.dto;

import com.ledgerone.entity.AlertSeverity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RiskDtos {
    private RiskDtos() {}

    public record RiskAlertResponse(
            UUID id,
            UUID portfolioId,
            AlertSeverity severity,
            String alertType,
            String message,
            boolean resolved,
            Instant createdAt) {}

    public record RiskSummaryResponse(
            UUID portfolioId,
            int riskScore,
            BigDecimal concentrationRisk,
            BigDecimal diversificationScore,
            BigDecimal dailyExposure,
            Map<String, BigDecimal> sectorAllocation,
            List<RiskAlertResponse> alerts) {}
}
