CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    cash_balance NUMERIC(19,4) NOT NULL,
    realized_profit NUMERIC(19,4) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stocks (
    id UUID PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    company_name VARCHAR(160) NOT NULL,
    sector VARCHAR(80) NOT NULL,
    last_price NUMERIC(19,4) NOT NULL,
    active BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE holdings (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    stock_id UUID NOT NULL REFERENCES stocks(id),
    quantity NUMERIC(19,6) NOT NULL,
    average_cost NUMERIC(19,4) NOT NULL,
    realized_profit NUMERIC(19,4) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_holdings_portfolio_stock UNIQUE (portfolio_id, stock_id)
);

CREATE TABLE trade_orders (
    id UUID PRIMARY KEY,
    client_order_id VARCHAR(80) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    stock_id UUID NOT NULL REFERENCES stocks(id),
    side VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    limit_price NUMERIC(19,4),
    execution_price NUMERIC(19,4),
    fees NUMERIC(19,4) NOT NULL,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    filled_at TIMESTAMPTZ,
    CONSTRAINT uk_trade_orders_user_client UNIQUE (user_id, client_order_id)
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES trade_orders(id),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    user_id UUID NOT NULL REFERENCES users(id),
    stock_id UUID NOT NULL REFERENCES stocks(id),
    action VARCHAR(30) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    fees NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE price_history (
    id UUID PRIMARY KEY,
    stock_id UUID NOT NULL REFERENCES stocks(id),
    price NUMERIC(19,4) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE watchlist_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    stock_id UUID NOT NULL REFERENCES stocks(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_watchlist_user_stock UNIQUE (user_id, stock_id)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action VARCHAR(40) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_alerts (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    severity VARCHAR(20) NOT NULL,
    alert_type VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    resolved BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(700) NOT NULL,
    read_flag BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_portfolios_user ON portfolios(user_id);
CREATE INDEX idx_holdings_portfolio ON holdings(portfolio_id);
CREATE INDEX idx_orders_portfolio_status ON trade_orders(portfolio_id, status);
CREATE INDEX idx_transactions_portfolio_created ON ledger_transactions(portfolio_id, created_at DESC);
CREATE INDEX idx_price_history_stock_observed ON price_history(stock_id, observed_at DESC);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_risk_open ON risk_alerts(resolved, created_at DESC);

INSERT INTO roles (id, name) VALUES (1, 'USER'), (2, 'ADMIN');

INSERT INTO users (id, email, password_hash, full_name, status, enabled, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'admin@ledgerone.com', '$2y$12$tleZJGZpdExu.DcAecTaK.goVxllSgvJUNO0Mv27RwcyACRoIJw4a', 'LedgerOne Administrator', 'ACTIVE', TRUE, NOW() - INTERVAL '45 days', NOW()),
('22222222-2222-2222-2222-222222222222', 'user@ledgerone.com', '$2y$12$htEZEHKFiFq94xJ/JKow/ev8SIBexiw7NW7FTFNI3ET1rdtkjRw8C', 'Ashish Mishra', 'ACTIVE', TRUE, NOW() - INTERVAL '30 days', NOW());

INSERT INTO user_roles (user_id, role_id) VALUES
('11111111-1111-1111-1111-111111111111', 1),
('11111111-1111-1111-1111-111111111111', 2),
('22222222-2222-2222-2222-222222222222', 1);

INSERT INTO stocks (id, symbol, company_name, sector, last_price, active, updated_at) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'AAPL', 'Apple Inc.', 'Technology', 214.3200, TRUE, NOW()),
('aaaaaaaa-0000-0000-0000-000000000002', 'MSFT', 'Microsoft Corporation', 'Technology', 486.1000, TRUE, NOW()),
('aaaaaaaa-0000-0000-0000-000000000003', 'NVDA', 'NVIDIA Corporation', 'Semiconductors', 143.8500, TRUE, NOW()),
('aaaaaaaa-0000-0000-0000-000000000004', 'JPM', 'JPMorgan Chase & Co.', 'Financial Services', 292.4500, TRUE, NOW()),
('aaaaaaaa-0000-0000-0000-000000000005', 'AMZN', 'Amazon.com, Inc.', 'Consumer Discretionary', 220.7600, TRUE, NOW()),
('aaaaaaaa-0000-0000-0000-000000000006', 'META', 'Meta Platforms, Inc.', 'Communication Services', 711.6400, TRUE, NOW()),
('aaaaaaaa-0000-0000-0000-000000000007', 'GOOGL', 'Alphabet Inc.', 'Communication Services', 199.1700, TRUE, NOW());

INSERT INTO portfolios (id, user_id, name, cash_balance, realized_profit, active, created_at, updated_at) VALUES
('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'Core Growth Portfolio', 48250.0000, 1350.0000, TRUE, NOW() - INTERVAL '28 days', NOW()),
('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'Admin Oversight Portfolio', 100000.0000, 0.0000, TRUE, NOW() - INTERVAL '40 days', NOW());

INSERT INTO holdings (id, portfolio_id, stock_id, quantity, average_cost, realized_profit, updated_at) VALUES
('55555555-5555-5555-5555-555555555551', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001', 42.000000, 178.5000, 250.0000, NOW()),
('55555555-5555-5555-5555-555555555552', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000002', 18.000000, 376.2000, 0.0000, NOW()),
('55555555-5555-5555-5555-555555555553', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000003', 65.000000, 103.9000, 600.0000, NOW()),
('55555555-5555-5555-5555-555555555554', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000004', 24.000000, 219.4000, 500.0000, NOW()),
('55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000005', 16.000000, 184.1000, 0.0000, NOW());

INSERT INTO trade_orders (id, client_order_id, user_id, portfolio_id, stock_id, side, type, status, quantity, limit_price, execution_price, fees, rejection_reason, created_at, updated_at, filled_at) VALUES
('66666666-6666-6666-6666-666666666661', 'seed-aapl-buy', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001', 'BUY', 'MARKET', 'FILLED', 42.000000, NULL, 178.5000, 7.4970, NULL, NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days'),
('66666666-6666-6666-6666-666666666662', 'seed-jpm-buy', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000004', 'BUY', 'MARKET', 'FILLED', 24.000000, NULL, 219.4000, 5.2656, NULL, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
('66666666-6666-6666-6666-666666666663', 'seed-meta-limit', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000006', 'BUY', 'LIMIT', 'PENDING', 5.000000, 680.0000, NULL, 3.4000, NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', NULL);

INSERT INTO ledger_transactions (id, order_id, portfolio_id, user_id, stock_id, action, price, quantity, fees, created_at) VALUES
('77777777-7777-7777-7777-777777777771', '66666666-6666-6666-6666-666666666661', '33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000001', 'BUY', 178.5000, 42.000000, 7.4970, NOW() - INTERVAL '22 days'),
('77777777-7777-7777-7777-777777777772', '66666666-6666-6666-6666-666666666662', '33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000004', 'BUY', 219.4000, 24.000000, 5.2656, NOW() - INTERVAL '14 days');

INSERT INTO price_history (id, stock_id, price, observed_at)
SELECT gen_random_uuid(), s.id,
       ROUND((s.last_price * (1 + ((series.day - 30)::NUMERIC * 0.0035) + ((ASCII(SUBSTRING(s.symbol, 1, 1)) % 7)::NUMERIC * 0.001)))::NUMERIC, 4),
       NOW() - ((30 - series.day) || ' days')::INTERVAL
FROM stocks s
CROSS JOIN generate_series(1, 30) AS series(day);

INSERT INTO watchlist_items (id, user_id, stock_id, created_at) VALUES
('88888888-8888-8888-8888-888888888881', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000006', NOW() - INTERVAL '6 days'),
('88888888-8888-8888-8888-888888888882', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000007', NOW() - INTERVAL '5 days'),
('88888888-8888-8888-8888-888888888883', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000003', NOW() - INTERVAL '2 days');

INSERT INTO audit_logs (id, user_id, action, subject, details, ip_address, created_at) VALUES
('99999999-9999-9999-9999-999999999991', '22222222-2222-2222-2222-222222222222', 'LOGIN', 'Login', 'Successful login', '127.0.0.1', NOW() - INTERVAL '2 hours'),
('99999999-9999-9999-9999-999999999992', '22222222-2222-2222-2222-222222222222', 'ORDER_PLACEMENT', 'Order placed', 'seed-meta-limit', '127.0.0.1', NOW() - INTERVAL '1 day'),
('99999999-9999-9999-9999-999999999993', '11111111-1111-1111-1111-111111111111', 'ADMIN_ACTION', 'Review risk queue', 'Admin reviewed open risk alerts', '127.0.0.1', NOW() - INTERVAL '3 hours');

INSERT INTO risk_alerts (id, portfolio_id, severity, alert_type, message, resolved, created_at) VALUES
('abababab-abab-abab-abab-ababababab01', '33333333-3333-3333-3333-333333333333', 'MEDIUM', 'SECTOR_ALLOCATION', 'Technology allocation is elevated compared with internal model thresholds', FALSE, NOW() - INTERVAL '12 hours');

INSERT INTO notifications (id, user_id, type, title, message, read_flag, created_at) VALUES
('cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcd01', '22222222-2222-2222-2222-222222222222', 'ORDER_FILLED', 'Order filled', 'AAPL market order filled at $178.50', FALSE, NOW() - INTERVAL '22 days'),
('cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcd02', '22222222-2222-2222-2222-222222222222', 'RISK_ALERT', 'Risk alert', 'Technology allocation is elevated compared with internal model thresholds', FALSE, NOW() - INTERVAL '12 hours');
