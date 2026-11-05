\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE ledger_probe (
    portfolio_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    amount NUMERIC(19,4) NOT NULL
);
INSERT INTO ledger_probe
SELECT md5((n % 1000)::text)::uuid,
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + n * INTERVAL '1 second',
       n % 100
FROM generate_series(1, 300000) AS n;
ANALYZE ledger_probe;

\echo 'Baseline: unindexed portfolio history'
EXPLAIN (ANALYZE, BUFFERS)
SELECT created_at, amount FROM ledger_probe WHERE portfolio_id = md5('42')::uuid
ORDER BY created_at DESC LIMIT 50;

CREATE INDEX ON ledger_probe (portfolio_id, created_at DESC);
ANALYZE ledger_probe;

\echo 'Candidate: same key order as idx_transactions_portfolio_created'
EXPLAIN (ANALYZE, BUFFERS)
SELECT created_at, amount FROM ledger_probe WHERE portfolio_id = md5('42')::uuid
ORDER BY created_at DESC LIMIT 50;
ROLLBACK;
