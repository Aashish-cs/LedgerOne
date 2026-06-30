ALTER TABLE users ADD COLUMN account_cash_balance NUMERIC(19,4);

UPDATE users u
SET account_cash_balance = GREATEST(
        100000.0000 - COALESCE((
            SELECT SUM(p.cash_balance + COALESCE(h.market_value, 0))
            FROM portfolios p
            LEFT JOIN (
                SELECT holding.portfolio_id, SUM(holding.quantity * stock.last_price) AS market_value
                FROM holdings holding
                JOIN stocks stock ON stock.id = holding.stock_id
                GROUP BY holding.portfolio_id
            ) h ON h.portfolio_id = p.id
            WHERE p.user_id = u.id AND p.active = TRUE
        ), 0),
        0
    );

ALTER TABLE users ALTER COLUMN account_cash_balance SET DEFAULT 100000.0000;
ALTER TABLE users ALTER COLUMN account_cash_balance SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_account_cash_nonnegative CHECK (account_cash_balance >= 0);

ALTER TABLE portfolios
    ADD CONSTRAINT chk_portfolios_cash_nonnegative CHECK (cash_balance >= 0);

CREATE UNIQUE INDEX uk_portfolios_user_active_name_ci
    ON portfolios (user_id, lower(name))
    WHERE active = TRUE;
