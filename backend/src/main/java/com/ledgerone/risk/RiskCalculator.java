package com.ledgerone.risk;

import com.ledgerone.entity.Holding;
import com.ledgerone.service.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RiskCalculator {
    public RiskProfile calculate(List<Holding> holdings, BigDecimal cashBalance) {
        BigDecimal marketValue = holdings.stream()
                .map(holding -> Money.money(holding.getQuantity().multiply(holding.getStock().getLastPrice())))
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal totalValue = Money.money(cashBalance.add(marketValue));
        BigDecimal maxPositionPercent = holdings.stream()
                .map(holding -> Money.percent(Money.money(holding.getQuantity().multiply(holding.getStock().getLastPrice())), marketValue))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        Map<String, BigDecimal> sectorAllocation = sectorAllocation(holdings, marketValue);
        BigDecimal diversificationScore = BigDecimal.valueOf(Math.min(100, holdings.size() * 12 + sectorAllocation.size() * 14))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyExposure = Money.percent(marketValue, totalValue);
        int riskScore = clamp(BigDecimal.valueOf(25)
                .add(maxPositionPercent.multiply(new BigDecimal("0.55")))
                .add(dailyExposure.multiply(new BigDecimal("0.20")))
                .subtract(diversificationScore.multiply(new BigDecimal("0.18")))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue());
        return new RiskProfile(riskScore, maxPositionPercent, diversificationScore, dailyExposure, sectorAllocation);
    }

    private Map<String, BigDecimal> sectorAllocation(List<Holding> holdings, BigDecimal marketValue) {
        Map<String, BigDecimal> sectorValue = new LinkedHashMap<>();
        holdings.forEach(holding -> {
            BigDecimal value = Money.money(holding.getQuantity().multiply(holding.getStock().getLastPrice()));
            sectorValue.merge(holding.getStock().getSector(), value, BigDecimal::add);
        });
        Map<String, BigDecimal> allocation = new LinkedHashMap<>();
        sectorValue.forEach((sector, value) -> allocation.put(sector, Money.percent(value, marketValue)));
        return allocation;
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(100, value));
    }

    public record RiskProfile(
            int riskScore,
            BigDecimal concentrationRisk,
            BigDecimal diversificationScore,
            BigDecimal dailyExposure,
            Map<String, BigDecimal> sectorAllocation) {}
}
