package com.ledgerone.service;

import com.ledgerone.dto.RiskDtos;
import com.ledgerone.entity.AlertSeverity;
import com.ledgerone.entity.Holding;
import com.ledgerone.entity.NotificationType;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.RiskAlert;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.mapper.RiskAlertMapper;
import com.ledgerone.notification.NotificationService;
import com.ledgerone.repository.HoldingRepository;
import com.ledgerone.repository.RiskAlertRepository;
import com.ledgerone.risk.RiskCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RiskService {
    private final HoldingRepository holdingRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final RiskAlertMapper riskAlertMapper;
    private final RiskCalculator riskCalculator;
    private final PortfolioService portfolioService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public RiskDtos.RiskSummaryResponse evaluate(UserAccount user, UUID portfolioId) {
        return summarize(portfolioService.getOwnedPortfolio(user, portfolioId), false);
    }

    @Transactional
    public RiskDtos.RiskSummaryResponse evaluateAndPersist(Portfolio portfolio) {
        return summarize(portfolio, true);
    }

    private RiskDtos.RiskSummaryResponse summarize(Portfolio portfolio, boolean persistAlerts) {
        List<Holding> holdings = holdingRepository.findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO);
        RiskCalculator.RiskProfile profile = riskCalculator.calculate(holdings, portfolio.getCashBalance());
        List<RiskAlert> generated = generatedAlerts(portfolio, profile);
        if (persistAlerts && !generated.isEmpty()) {
            riskAlertRepository.saveAll(generated);
            generated.stream()
                    .filter(alert -> alert.getSeverity() == AlertSeverity.HIGH || alert.getSeverity() == AlertSeverity.CRITICAL)
                    .findFirst()
                    .ifPresent(alert -> notificationService.create(
                            portfolio.getUser(), NotificationType.RISK_ALERT, "Risk alert", alert.getMessage()));
        }
        List<RiskDtos.RiskAlertResponse> alerts = riskAlertRepository.findTop10ByPortfolioOrderByCreatedAtDesc(portfolio).stream()
                .map(riskAlertMapper::toResponse)
                .toList();
        return new RiskDtos.RiskSummaryResponse(
                portfolio.getId(),
                profile.riskScore(),
                profile.concentrationRisk(),
                profile.diversificationScore(),
                profile.dailyExposure(),
                profile.sectorAllocation(),
                alerts);
    }

    private List<RiskAlert> generatedAlerts(Portfolio portfolio, RiskCalculator.RiskProfile profile) {
        List<RiskAlert> alerts = new ArrayList<>();
        if (profile.concentrationRisk().compareTo(BigDecimal.valueOf(35)) > 0) {
            alerts.add(alert(
                    portfolio,
                    AlertSeverity.HIGH,
                    "CONCENTRATION",
                    "Largest single position exceeds 35% of portfolio market value"));
        }
        if (profile.sectorAllocation().values().stream().anyMatch(percent -> percent.compareTo(BigDecimal.valueOf(55)) > 0)) {
            alerts.add(alert(
                    portfolio,
                    AlertSeverity.MEDIUM,
                    "SECTOR_ALLOCATION",
                    "Sector allocation exceeds internal diversification threshold"));
        }
        if (profile.dailyExposure().compareTo(BigDecimal.valueOf(95)) > 0) {
            alerts.add(alert(
                    portfolio,
                    AlertSeverity.MEDIUM,
                    "DAILY_EXPOSURE",
                    "Portfolio is more than 95% invested with minimal cash buffer"));
        }
        return alerts;
    }

    private RiskAlert alert(Portfolio portfolio, AlertSeverity severity, String type, String message) {
        RiskAlert alert = new RiskAlert();
        alert.setPortfolio(portfolio);
        alert.setSeverity(severity);
        alert.setAlertType(type);
        alert.setMessage(message);
        return alert;
    }
}
