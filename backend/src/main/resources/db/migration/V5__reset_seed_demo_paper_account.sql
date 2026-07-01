WITH demo_user AS (
    SELECT id
    FROM users
    WHERE email = 'user@ledgerone.com'
),
demo_portfolios AS (
    SELECT p.id
    FROM portfolios p
    JOIN demo_user u ON u.id = p.user_id
)
DELETE FROM ledger_transactions t
USING demo_portfolios p
WHERE t.portfolio_id = p.id;

WITH demo_user AS (
    SELECT id
    FROM users
    WHERE email = 'user@ledgerone.com'
),
demo_portfolios AS (
    SELECT p.id
    FROM portfolios p
    JOIN demo_user u ON u.id = p.user_id
)
DELETE FROM trade_orders o
USING demo_portfolios p
WHERE o.portfolio_id = p.id;

WITH demo_user AS (
    SELECT id
    FROM users
    WHERE email = 'user@ledgerone.com'
),
demo_portfolios AS (
    SELECT p.id
    FROM portfolios p
    JOIN demo_user u ON u.id = p.user_id
)
DELETE FROM holdings h
USING demo_portfolios p
WHERE h.portfolio_id = p.id;

WITH demo_user AS (
    SELECT id
    FROM users
    WHERE email = 'user@ledgerone.com'
),
demo_portfolios AS (
    SELECT p.id
    FROM portfolios p
    JOIN demo_user u ON u.id = p.user_id
)
DELETE FROM risk_alerts r
USING demo_portfolios p
WHERE r.portfolio_id = p.id;

WITH demo_user AS (
    SELECT id
    FROM users
    WHERE email = 'user@ledgerone.com'
),
demo_account AS (
    SELECT DISTINCT ON (p.user_id) p.id, p.user_id
    FROM portfolios p
    JOIN demo_user u ON u.id = p.user_id
    ORDER BY p.user_id, p.created_at ASC
)
UPDATE portfolios p
SET active = FALSE,
    cash_balance = 0.0000,
    updated_at = NOW()
FROM demo_account a
WHERE p.user_id = a.user_id
  AND p.id <> a.id;

WITH demo_user AS (
    SELECT id
    FROM users
    WHERE email = 'user@ledgerone.com'
),
demo_account AS (
    SELECT DISTINCT ON (p.user_id) p.id
    FROM portfolios p
    JOIN demo_user u ON u.id = p.user_id
    ORDER BY p.user_id, p.created_at ASC
)
UPDATE portfolios p
SET name = 'Paper Trading Account',
    cash_balance = 100000.0000,
    realized_profit = 0.0000,
    active = TRUE,
    updated_at = NOW()
FROM demo_account a
WHERE p.id = a.id;

UPDATE users
SET account_cash_balance = 0.0000,
    updated_at = NOW()
WHERE email = 'user@ledgerone.com';
