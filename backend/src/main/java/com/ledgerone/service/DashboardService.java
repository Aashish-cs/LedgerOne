package com.ledgerone.service;

import com.ledgerone.dto.DashboardDtos;
import com.ledgerone.dto.PortfolioDtos;
import com.ledgerone.dto.RiskDtos;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.Holding;
import com.ledgerone.entity.OrderStatus;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.mapper.TransactionMapper;
import com.ledgerone.repository.HoldingRepository;
import com.ledgerone.repository.LedgerTransactionRepository;
import com.ledgerone.repository.PriceHistoryRepository;
import com.ledgerone.repository.TradeOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final PortfolioService portfolioService;
    private final RiskService riskService;
    private final HoldingRepository holdingRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse dashboard(UserAccount user, UUID requestedPortfolioId) {
        Optional<Portfolio> selectedPortfolio = requestedPortfolioId == null
                ? portfolioService.findDefaultPortfolio(user)
                : Optional.of(portfolioService.getOwnedPortfolio(user, requestedPortfolioId));
        if (selectedPortfolio.isEmpty()) {
            return accountOnlyDashboard(user);
        }
        Portfolio portfolio = selectedPortfolio.get();
        PortfolioDtos.PortfolioResponse portfolioResponse = portfolioService.toResponse(portfolio);
        RiskDtos.RiskSummaryResponse risk = riskService.evaluate(user, portfolio.getId());
        List<DashboardDtos.PerformancePoint> performance = performance(portfolio);
        BigDecimal dailyProfit = performance.size() < 2
                ? portfolioResponse.unrealizedProfit()
                : Money.money(performance.get(performance.size() - 1).value().subtract(performance.get(performance.size() - 2).value()));
        BigDecimal monthlyProfit = performance.isEmpty()
                ? portfolioResponse.unrealizedProfit()
                : Money.money(portfolioResponse.totalValue().subtract(performance.getFirst().value()));
        BigDecimal totalReturn = Money.money(portfolioResponse.realizedProfit().add(portfolioResponse.unrealizedProfit()));
        long openOrders = tradeOrderRepository.countByPortfolioAndStatus(portfolio, OrderStatus.PENDING);
        List<TradingDtos.TransactionResponse> recentTransactions = ledgerTransactionRepository.findTop8ByPortfolioOrderByCreatedAtDesc(portfolio).stream()
                .map(transactionMapper::toResponse)
                .toList();
        return new DashboardDtos.DashboardResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolioResponse.totalValue(),
                portfolioResponse.cashBalance(),
                dailyProfit,
                monthlyProfit,
                totalReturn,
                openOrders,
                risk.riskScore(),
                List.of(
                        new DashboardDtos.MetricCard("Account Value", portfolioResponse.totalValue(), Money.percent(totalReturn, portfolioResponse.totalValue())),
                        new DashboardDtos.MetricCard("Cash Balance", portfolioResponse.cashBalance(), Money.percent(portfolioResponse.cashBalance(), portfolioResponse.totalValue())),
                        new DashboardDtos.MetricCard("Daily Profit", dailyProfit, Money.percent(dailyProfit, portfolioResponse.totalValue())),
                        new DashboardDtos.MetricCard("Monthly Profit", monthlyProfit, Money.percent(monthlyProfit, portfolioResponse.totalValue())),
                        new DashboardDtos.MetricCard("Open Orders", BigDecimal.valueOf(openOrders), BigDecimal.ZERO),
                        new DashboardDtos.MetricCard("Risk Score", BigDecimal.valueOf(risk.riskScore()), BigDecimal.ZERO)),
                portfolioResponse.allocation(),
                performance,
                recentTransactions,
                risk.alerts());
    }

    private DashboardDtos.DashboardResponse accountOnlyDashboard(UserAccount user) {
        BigDecimal availableCash = Money.money(user.getAccountCashBalance());
        return new DashboardDtos.DashboardResponse(
                null,
                "Paper Trading Account",
                availableCash,
                availableCash,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                0,
                0,
                List.of(
                        new DashboardDtos.MetricCard("Available Cash", availableCash, BigDecimal.ZERO),
                        new DashboardDtos.MetricCard("Account Value", Money.ZERO, BigDecimal.ZERO),
                        new DashboardDtos.MetricCard("Daily Profit", Money.ZERO, BigDecimal.ZERO),
                        new DashboardDtos.MetricCard("Monthly Profit", Money.ZERO, BigDecimal.ZERO),
                        new DashboardDtos.MetricCard("Open Orders", BigDecimal.ZERO, BigDecimal.ZERO),
                        new DashboardDtos.MetricCard("Risk Score", BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(),
                List.of(new DashboardDtos.PerformancePoint(Instant.now(), availableCash)),
                List.of(),
                List.of());
    }

    private List<DashboardDtos.PerformancePoint> performance(Portfolio portfolio) {
        List<Holding> holdings = holdingRepository.findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO);
        if (holdings.isEmpty()) {
            return List.of(new DashboardDtos.PerformancePoint(Instant.now(), Money.money(portfolio.getCashBalance())));
        }
        List<DashboardDtos.PerformancePoint> points = new ArrayList<>();
        Holding anchor = holdings.stream()
                .max(Comparator.comparing(holding -> holding.getQuantity().multiply(holding.getStock().getLastPrice())))
                .orElse(holdings.getFirst());
        priceHistoryRepository.findTop40ByStockOrderByObservedAtDesc(anchor.getStock()).stream()
                .sorted(Comparator.comparing(com.ledgerone.entity.PriceHistory::getObservedAt))
                .forEach(point -> {
                    BigDecimal ratio = point.getPrice().divide(anchor.getStock().getLastPrice(), 6, java.math.RoundingMode.HALF_UP);
                    BigDecimal currentMarketValue = holdings.stream()
                            .map(holding -> holding.getQuantity().multiply(holding.getStock().getLastPrice()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal estimated = portfolio.getCashBalance().add(currentMarketValue.multiply(ratio));
                    points.add(new DashboardDtos.PerformancePoint(point.getObservedAt(), Money.money(estimated)));
                });
        if (points.isEmpty()) {
            BigDecimal value = portfolio.getCashBalance().add(holdings.stream()
                    .map(holding -> holding.getQuantity().multiply(holding.getStock().getLastPrice()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            points.add(new DashboardDtos.PerformancePoint(Instant.now(), Money.money(value)));
        }
        return points;
    }
}
