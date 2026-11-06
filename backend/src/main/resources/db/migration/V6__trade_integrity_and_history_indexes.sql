ALTER TABLE holdings ADD CONSTRAINT chk_holdings_quantity_nonnegative CHECK (quantity >= 0);
ALTER TABLE trade_orders ADD CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0);
ALTER TABLE trade_orders ADD CONSTRAINT chk_orders_limit_positive CHECK (limit_price IS NULL OR limit_price > 0);
CREATE UNIQUE INDEX uk_ledger_order_once ON ledger_transactions(order_id);
CREATE INDEX idx_orders_portfolio_created ON trade_orders(portfolio_id, created_at DESC);
CREATE INDEX idx_orders_pending_created ON trade_orders(created_at, id) WHERE status = 'PENDING';
