package com.ledgerone.service;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.PortfolioDtos;
import com.ledgerone.entity.AuditAction;
import com.ledgerone.entity.Holding;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.BadRequestException;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.repository.HoldingRepository;
import com.ledgerone.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PortfolioDtos.PortfolioResponse> list(UserAccount user) {
        return portfolioRepository.findByUserAndActiveTrue(user).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(PortfolioDtos.PortfolioResponse::createdAt))
                .toList();
    }

    @Transactional
    public PortfolioDtos.PortfolioResponse create(UserAccount user, PortfolioDtos.PortfolioRequest request) {
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        portfolio.setName(request.name());
        portfolio.setCashBalance(new BigDecimal("50000.0000"));
        Portfolio saved = portfolioRepository.save(portfolio);
        auditService.record(user, AuditAction.PORTFOLIO_UPDATE, "Portfolio created", saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public PortfolioDtos.PortfolioResponse rename(UserAccount user, UUID portfolioId, PortfolioDtos.PortfolioRequest request) {
        Portfolio portfolio = getOwnedPortfolio(user, portfolioId);
        portfolio.setName(request.name());
        auditService.record(user, AuditAction.PORTFOLIO_UPDATE, "Portfolio renamed", portfolio.getName());
        return toResponse(portfolio);
    }

    @Transactional
    public void delete(UserAccount user, UUID portfolioId) {
        Portfolio portfolio = getOwnedPortfolio(user, portfolioId);
        List<Holding> holdings = holdingRepository.findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO);
        if (!holdings.isEmpty()) {
            throw new BadRequestException("Liquidate holdings before deleting a portfolio");
        }
        portfolio.setActive(false);
        auditService.record(user, AuditAction.PORTFOLIO_UPDATE, "Portfolio deleted", portfolio.getName());
    }

    @Transactional(readOnly = true)
    public Portfolio getOwnedPortfolio(UserAccount user, UUID portfolioId) {
        return portfolioRepository
                .findByIdAndUserAndActiveTrue(portfolioId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }

    @Transactional(readOnly = true)
    public Portfolio defaultPortfolio(UserAccount user) {
        return portfolioRepository
                .findFirstByUserAndActiveTrueOrderByCreatedAtAsc(user)
                .orElseThrow(() -> new ResourceNotFoundException("No active portfolio found"));
    }

    public PortfolioDtos.PortfolioResponse toResponse(Portfolio portfolio) {
        List<Holding> holdings = holdingRepository.findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO);
        BigDecimal marketValue = holdings.stream()
                .map(holding -> Money.money(holding.getQuantity().multiply(holding.getStock().getLastPrice())))
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal totalValue = Money.money(portfolio.getCashBalance().add(marketValue));
        List<PortfolioDtos.HoldingResponse> holdingResponses = holdings.stream()
                .map(holding -> holdingResponse(holding, marketValue))
                .sorted(Comparator.comparing(PortfolioDtos.HoldingResponse::marketValue).reversed())
                .toList();
        return new PortfolioDtos.PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                Money.money(portfolio.getCashBalance()),
                Money.money(marketValue),
                totalValue,
                Money.money(portfolio.getRealizedProfit()),
                Money.money(holdingResponses.stream()
                        .map(PortfolioDtos.HoldingResponse::unrealizedProfit)
                        .reduce(Money.ZERO, BigDecimal::add)),
                holdingResponses,
                allocation(holdings, marketValue),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }

    private PortfolioDtos.HoldingResponse holdingResponse(Holding holding, BigDecimal totalMarketValue) {
        BigDecimal marketValue = Money.money(holding.getQuantity().multiply(holding.getStock().getLastPrice()));
        BigDecimal costBasis = Money.money(holding.getQuantity().multiply(holding.getAverageCost()));
        BigDecimal unrealized = Money.money(marketValue.subtract(costBasis));
        return new PortfolioDtos.HoldingResponse(
                holding.getId(),
                holding.getStock().getSymbol(),
                holding.getStock().getCompanyName(),
                holding.getStock().getSector(),
                Money.quantity(holding.getQuantity()),
                Money.money(holding.getAverageCost()),
                Money.money(holding.getStock().getLastPrice()),
                marketValue,
                unrealized,
                Money.money(holding.getRealizedProfit()),
                Money.percent(marketValue, totalMarketValue));
    }

    private List<PortfolioDtos.AllocationSlice> allocation(List<Holding> holdings, BigDecimal totalMarketValue) {
        Map<String, BigDecimal> bySector = new LinkedHashMap<>();
        holdings.forEach(holding -> {
            BigDecimal value = Money.money(holding.getQuantity().multiply(holding.getStock().getLastPrice()));
            bySector.merge(holding.getStock().getSector(), value, BigDecimal::add);
        });
        return bySector.entrySet().stream()
                .map(entry -> new PortfolioDtos.AllocationSlice(
                        entry.getKey(), Money.money(entry.getValue()), Money.percent(entry.getValue(), totalMarketValue)))
                .sorted(Comparator.comparing(PortfolioDtos.AllocationSlice::value).reversed())
                .toList();
    }
}
