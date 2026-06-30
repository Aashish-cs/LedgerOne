CREATE TEMP TABLE ledgerone_v4_created_accounts (
    user_id UUID PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO ledgerone_v4_created_accounts (user_id)
SELECT u.id
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM portfolios p
    WHERE p.user_id = u.id
      AND p.active = TRUE
);

INSERT INTO portfolios (id, user_id, name, cash_balance, realized_profit, active, created_at, updated_at)
SELECT gen_random_uuid(),
       u.id,
       'Paper Trading Account',
       0.0000,
       0.0000,
       TRUE,
       NOW(),
       NOW()
FROM users u
JOIN ledgerone_v4_created_accounts c ON c.user_id = u.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
),
extra_account_totals AS (
    SELECT p.user_id,
           SUM(p.cash_balance) AS cash_balance,
           SUM(p.realized_profit) AS realized_profit
    FROM portfolios p
    JOIN first_account fa ON fa.user_id = p.user_id
    WHERE p.active = TRUE
      AND p.id <> fa.id
    GROUP BY p.user_id
)
UPDATE portfolios p
SET cash_balance = p.cash_balance
        + CASE
            WHEN u.account_cash_balance > 0 THEN u.account_cash_balance
            WHEN c.user_id IS NOT NULL THEN 100000.0000
            ELSE 0.0000
          END
        + COALESCE(e.cash_balance, 0.0000),
    realized_profit = p.realized_profit + COALESCE(e.realized_profit, 0.0000),
    updated_at = NOW()
FROM users u
JOIN first_account fa ON fa.user_id = u.id
LEFT JOIN extra_account_totals e ON e.user_id = u.id
LEFT JOIN ledgerone_v4_created_accounts c ON c.user_id = u.id
WHERE p.id = fa.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
),
merged_holdings AS (
    SELECT fa.id AS portfolio_id,
           h.stock_id,
           SUM(h.quantity) AS quantity,
           CASE
               WHEN SUM(h.quantity) > 0
                   THEN SUM(h.quantity * h.average_cost) / SUM(h.quantity)
               ELSE 0.0000
           END AS average_cost,
           SUM(h.realized_profit) AS realized_profit
    FROM holdings h
    JOIN portfolios p ON p.id = h.portfolio_id
    JOIN first_account fa ON fa.user_id = p.user_id
    WHERE p.active = TRUE
    GROUP BY fa.id, h.stock_id
    HAVING SUM(h.quantity) > 0
)
INSERT INTO holdings (id, portfolio_id, stock_id, quantity, average_cost, realized_profit, updated_at)
SELECT gen_random_uuid(),
       portfolio_id,
       stock_id,
       quantity,
       ROUND(average_cost, 4),
       realized_profit,
       NOW()
FROM merged_holdings
ON CONFLICT (portfolio_id, stock_id)
DO UPDATE SET quantity = EXCLUDED.quantity,
              average_cost = EXCLUDED.average_cost,
              realized_profit = EXCLUDED.realized_profit,
              updated_at = NOW();

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
)
DELETE FROM holdings h
USING portfolios p, first_account fa
WHERE h.portfolio_id = p.id
  AND p.user_id = fa.user_id
  AND p.active = TRUE
  AND p.id <> fa.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
)
UPDATE trade_orders o
SET portfolio_id = fa.id
FROM portfolios p
JOIN first_account fa ON fa.user_id = p.user_id
WHERE o.portfolio_id = p.id
  AND p.active = TRUE
  AND p.id <> fa.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
)
UPDATE ledger_transactions t
SET portfolio_id = fa.id
FROM portfolios p
JOIN first_account fa ON fa.user_id = p.user_id
WHERE t.portfolio_id = p.id
  AND p.active = TRUE
  AND p.id <> fa.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
)
UPDATE risk_alerts r
SET portfolio_id = fa.id
FROM portfolios p
JOIN first_account fa ON fa.user_id = p.user_id
WHERE r.portfolio_id = p.id
  AND p.active = TRUE
  AND p.id <> fa.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id, user_id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
)
UPDATE portfolios p
SET cash_balance = 0.0000,
    active = FALSE,
    updated_at = NOW()
FROM first_account fa
WHERE p.user_id = fa.user_id
  AND p.active = TRUE
  AND p.id <> fa.id;

WITH first_account AS (
    SELECT DISTINCT ON (user_id) id
    FROM portfolios
    WHERE active = TRUE
    ORDER BY user_id, created_at ASC
)
UPDATE portfolios p
SET name = 'Paper Trading Account',
    updated_at = NOW()
FROM first_account fa
WHERE p.id = fa.id;

CREATE UNIQUE INDEX uk_portfolios_user_one_active
    ON portfolios (user_id)
    WHERE active = TRUE;

UPDATE users
SET account_cash_balance = 0.0000
WHERE account_cash_balance <> 0.0000;

ALTER TABLE users ALTER COLUMN account_cash_balance SET DEFAULT 0.0000;
