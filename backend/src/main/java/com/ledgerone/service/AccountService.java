package com.ledgerone.service;

import com.ledgerone.dto.AccountDtos;
import com.ledgerone.dto.PortfolioDtos;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.repository.UserAccountRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserAccountRepository userRepository;
    private final PortfolioService portfolioService;

    @Transactional(readOnly = true)
    public AccountDtos.PaperAccountResponse summary(UserAccount user) {
        UserAccount current = userRepository
                .findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        var portfolios = portfolioService.list(current);
        BigDecimal portfolioCash = portfolios.stream()
                .map(PortfolioDtos.PortfolioResponse::cashBalance)
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal marketValue = portfolios.stream()
                .map(PortfolioDtos.PortfolioResponse::marketValue)
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal availableCash = Money.money(current.getAccountCashBalance());
        return new AccountDtos.PaperAccountResponse(
                availableCash,
                Money.money(portfolioCash),
                Money.money(marketValue),
                Money.money(availableCash.add(portfolioCash).add(marketValue)),
                portfolios.size());
    }
}
