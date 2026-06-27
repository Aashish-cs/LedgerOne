package com.ledgerone.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record MetricCard(String label, BigDecimal value, BigDecimal changePercent) {}

    public record PerformancePoint(Instant timestamp, BigDecimal value) {}

    public record DashboardResponse(
            UUID portfolioId,
            String portfolioName,
            BigDecimal portfolioValue,
            BigDecimal cashBalance,
            BigDecimal dailyProfit,
            BigDecimal monthlyProfit,
            BigDecimal totalReturn,
            long openOrders,
            int riskScore,
            List<MetricCard> metrics,
            List<PortfolioDtos.AllocationSlice> allocation,
            List<PerformancePoint> performance,
            List<TradingDtos.TransactionResponse> recentTransactions,
            List<RiskDtos.RiskAlertResponse> riskAlerts) {}
}
