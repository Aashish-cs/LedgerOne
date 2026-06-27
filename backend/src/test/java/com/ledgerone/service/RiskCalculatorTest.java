package com.ledgerone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerone.entity.Holding;
import com.ledgerone.entity.Stock;
import com.ledgerone.risk.RiskCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskCalculatorTest {
    private final RiskCalculator calculator = new RiskCalculator();

    @Test
    void concentrationIncreasesRiskScore() {
        RiskCalculator.RiskProfile profile = calculator.calculate(
                List.of(holding("AAPL", "Technology", "90", "100"), holding("JPM", "Financial Services", "10", "100")),
                new BigDecimal("1000.00"));

        assertThat(profile.concentrationRisk()).isEqualByComparingTo("90.00");
        assertThat(profile.riskScore()).isGreaterThan(50);
        assertThat(profile.sectorAllocation()).containsKeys("Technology", "Financial Services");
    }

    private Holding holding(String symbol, String sector, String quantity, String lastPrice) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setSector(sector);
        stock.setCompanyName(symbol + " Inc.");
        stock.setLastPrice(new BigDecimal(lastPrice));
        Holding holding = new Holding();
        holding.setStock(stock);
        holding.setQuantity(new BigDecimal(quantity));
        holding.setAverageCost(new BigDecimal(lastPrice));
        return holding;
    }
}
