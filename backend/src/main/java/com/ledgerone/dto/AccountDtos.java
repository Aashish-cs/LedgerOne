package com.ledgerone.dto;

import java.math.BigDecimal;

public final class AccountDtos {
    private AccountDtos() {}

    public record PaperAccountResponse(
            BigDecimal availableCash,
            BigDecimal portfolioCash,
            BigDecimal marketValue,
            BigDecimal totalEquity,
            int activePortfolioCount) {}
}
