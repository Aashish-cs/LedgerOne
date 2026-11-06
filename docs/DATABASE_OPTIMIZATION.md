# Database Optimization

## Result

The **42% read/write latency reduction claim is UNVERIFIED**. The measured indexed
reads improved substantially; writes became slower. Those cannot honestly be combined
into a single improvement percentage.

## Reproduction

```bash
kubectl --context kind-ledgerone-audit -n ledgerone-local exec -i db-0 -- \
  psql -qAt -U ledgerone -d ledgerone < tests/performance/database.sql
```

[Raw JSON and execution plans](../tests/performance/results/database.json) contain
200 samples: 20 measured repetitions after 3 warmups for each phase/workload.
PostgreSQL 16, one session, 300,000 synthetic order rows, 1,000 balance rows.
The experiment uses temporary tables inside a transaction that is rolled back.
It never changes user data or drops real indexes. Timing is server-side
`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON, TIMING OFF)`, excluding client network time.

| Workload | Baseline median ms | Indexed median ms | Median change |
| --- | ---: | ---: | --- |
| Portfolio history, latest 50 | 16.284 | 0.105 | 99.36% lower read execution time |
| User orders, latest 50 | 15.083 | 0.111 | 99.26% lower read execution time |
| Symbol orders, latest 50 | 17.583 | 0.020 | 99.89% lower read execution time |
| Insert batch of 100 | 0.041 | 0.277 | 575.61% higher execution time |
| Primary-key balance update | 0.009 | 0.015 | 66.67% higher; unrelated table, tiny durations/noise |

All raw samples include plans and buffer counts. Baseline history queries scan and
sort; indexed history queries use composite keys. Candidates are
`(portfolio_id, created_at DESC)`, `(user_id, created_at DESC)`, and
`(symbol, created_at DESC)`. Only the portfolio-history order index corresponds
to a current application access path and was promoted in V6. The user/symbol
candidates are experiments, not claims about new API endpoints.

## Schema and Integrity

The migrated database has 14 application tables: roles, users, user_roles,
portfolios, stocks, holdings, trade_orders, ledger_transactions, price_history,
watchlist_items, audit_logs, risk_alerts, notifications, refresh_tokens.
The Flyway history table is metadata and counted separately. There are 13 entities
because user_roles is a many-to-many join table.

UUID primary keys identify domain records; foreign keys bind users, portfolios,
stocks, orders, holdings, and ledger rows. NUMERIC avoids binary floating-point
money arithmetic. Unique keys protect emails, stock symbols, one holding per
account/stock, watchlist entries, and user/client-order IDs. V3/V4 enforce
nonnegative cash and one active account per user. V6 adds nonnegative share
quantities, positive order quantities/prices, one ledger row per order, a portfolio
order-history index, and a partial pending-order index.

Trade transactions use PostgreSQL's default READ COMMITTED plus a
`PESSIMISTIC_WRITE` user-row lock. READ COMMITTED alone does not prevent double
spending. Requests for the same account serialize before reading balance or checking
duplicate IDs. Different accounts may proceed concurrently. Hibernate writes commit
together; a runtime failure rolls the entire trade back. Real database tests verify
concurrent buys, sells, duplicate requests, and failure after cash mutation.

## Limits and Recovery

Sequential phases are affected by caches and concurrent host activity. Temporary
tables do not model production WAL, disk durability, replication, or network latency.
The balance-update difference is not caused by an order-table index. These are
repeatable query experiments, not a production performance certification.

Previously applied migrations V1-V5 were not edited. V6 is forward-only. Check legacy
data for constraint violations before rollout. Back up the database before migrations;
recover via a reviewed forward fix or restore to an isolated database, not by casually
dropping tables or Flyway history. PostgreSQL integration tests apply V1-V6 to a clean
container and assert the actual table count.

Recommended wording: "Designed 14 Flyway-managed tables and transactional trade writes;
benchmarked composite indexes against repeatable portfolio-history queries."
