package com.ledgerone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.PortfolioDtos;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.BadRequestException;
import com.ledgerone.exception.ConflictException;
import com.ledgerone.repository.HoldingRepository;
import com.ledgerone.repository.PortfolioRepository;
import com.ledgerone.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private AuditService auditService;

    private PortfolioService portfolioService;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(portfolioRepository, holdingRepository, userRepository, auditService);
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("ashish@example.com");
        user.setFullName("Ashish Mishra");
        user.setAccountCashBalance(new BigDecimal("100000.0000"));
    }

    @Test
    void creatingPortfolioAllocatesCashFromPaperAccount() {
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(portfolioRepository.findFirstByUserAndActiveTrueOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());
        when(portfolioRepository.existsByUserAndActiveTrueAndNameIgnoreCase(user, "Tech Growth")).thenReturn(false);
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> {
            Portfolio portfolio = invocation.getArgument(0);
            portfolio.setId(UUID.randomUUID());
            return portfolio;
        });
        when(holdingRepository.findByPortfolioAndQuantityGreaterThan(any(Portfolio.class), eq(BigDecimal.ZERO)))
                .thenReturn(List.of());

        PortfolioDtos.PortfolioResponse response = portfolioService.create(
                user, new PortfolioDtos.PortfolioCreateRequest("  Tech   Growth  ", new BigDecimal("50000")));

        assertThat(user.getAccountCashBalance()).isEqualByComparingTo("50000.0000");
        assertThat(response.name()).isEqualTo("Tech Growth");
        assertThat(response.cashBalance()).isEqualByComparingTo("50000.0000");

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        assertThat(portfolioCaptor.getValue().getCashBalance()).isEqualByComparingTo("50000.0000");
    }

    @Test
    void creatingPortfolioRejectsAllocationAboveAvailableCash() {
        user.setAccountCashBalance(new BigDecimal("20000.0000"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(portfolioRepository.findFirstByUserAndActiveTrueOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());
        when(portfolioRepository.existsByUserAndActiveTrueAndNameIgnoreCase(user, "Dividend")).thenReturn(false);

        assertThatThrownBy(() -> portfolioService.create(
                        user, new PortfolioDtos.PortfolioCreateRequest("Dividend", new BigDecimal("30000"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Allocation exceeds available account cash");
    }

    @Test
    void creatingPortfolioRejectsDuplicateActiveName() {
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(portfolioRepository.findFirstByUserAndActiveTrueOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());
        when(portfolioRepository.existsByUserAndActiveTrueAndNameIgnoreCase(user, "Tech Growth")).thenReturn(true);

        assertThatThrownBy(() -> portfolioService.create(
                        user, new PortfolioDtos.PortfolioCreateRequest("Tech Growth", new BigDecimal("10000"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Portfolio name already exists");
    }

    @Test
    void creatingPortfolioRejectsSecondPaperTradingAccount() {
        Portfolio existing = new Portfolio();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setName("Paper Trading Account");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(portfolioRepository.findFirstByUserAndActiveTrueOrderByCreatedAtAsc(user)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> portfolioService.create(
                        user, new PortfolioDtos.PortfolioCreateRequest("Another Account", new BigDecimal("1000"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Each user has one paper trading account");
    }

    @Test
    void deletingPaperTradingAccountIsRejected() {
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setUser(user);
        portfolio.setName("Paper Trading Account");

        when(portfolioRepository.findByIdAndUserAndActiveTrue(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> portfolioService.delete(user, portfolio.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Paper trading account cannot be deleted");
    }
}
