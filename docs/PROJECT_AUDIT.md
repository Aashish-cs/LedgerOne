# Project Audit

Audit started 2026-11-05 on `distributed-trading-lab`, baseline `1cf941c`.
Working tree was clean. No AWS provisioning is authorized. The hosted Render/Vercel
version on `main` is separate; this work stays on the lab branch.

## Baseline Architecture

- React/TypeScript: `frontend/src/App.tsx` owns pages and forms; `api/client.ts`
  handles JWT requests and refresh, with optional Kafka submission/polling.
- Spring Boot: controllers -> transactional services -> JPA repositories -> PostgreSQL.
  Registration creates one account with $100,000 simulated cash. Extra account creation
  is intentionally rejected, not a missing feature.
- Synchronous `POST /api/orders` remains supported. The `order-api` profile adds
  `POST /api/orders/queued`; `order-worker` runs a Kafka consumer group. Six local
  partitions use user ID keys. Broker acknowledgements precede HTTP 202.
- Trade writes lock the user row, then atomically change orders, holdings, cash,
  ledger, audit, notifications, and risk. Limit orders reserve nothing and revalidate
  at execution. Kafka offsets follow the database commit; replays are idempotent.
- Five Flyway migrations create 14 business tables (13 entities plus user_roles).
  Flyway's schema history is an additional metadata table, not a business table.
- Docker Compose runs PostgreSQL, API, Kafka and workers. Kubernetes base/AWS overlays
  define probes and CPU HPA. Terraform in `deploy/aws` defines EKS/MSK/RDS/ECR.
- GitHub Actions runs unit/build checks, a Compose smoke test, and configuration
  validation. There is no automated AWS deployment.

## Findings Before Changes

| Area | Baseline status | Risk / action |
| --- | --- | --- |
| JWT, BCrypt, role checks | Implemented; JWT unit test and admin method security | JWT filter does not reject frozen/disabled accounts; disabled is ignored by UserDetails; fix and test |
| Refresh rotation | Implemented, hashes stored | Concurrent replay can rotate twice; frozen users can refresh; lock token row and check account |
| Registration | Implemented, unique email | Concurrent duplicate can surface as generic 500; normalize email and map integrity conflict |
| Orders / buying power | Implemented; 7 trading unit cases | Need real PostgreSQL concurrent buy/sell/rollback tests; service should reject invalid internal requests |
| Idempotency | Lookup plus DB unique constraint | Same ID with different payload returns old order; reject conflicting payload |
| Kafka | Ack-before-202, retries, DLT, commit-after-DB | DLT has no public durable receipt; 404 is ambiguous; document accurately and test actual broker behavior |
| UI chart | Partial / misleading | Sine-wave account performance and synthetic stock trend are labeled live; remove invented motion |
| Portfolio history | Estimated from one anchor stock | Not historical account NAV; use observed account snapshots without adding arbitrary tables |
| UI account refresh | Query invalidation exists | Cash/account summary must also refresh after fills; test buy/sell in browser |
| Quotes | Finnhub/cache/public fallbacks; 4 unit tests | Provider timestamps/freshness and external availability remain limitations |
| Database | Constraints/indexes/Flyway implemented | No real concurrency integration tests; historical 42% improvement not measured |
| Test suite | 22 passed, 0 failed, 0 skipped | Most tests mock persistence; entry-point smoke is not an integration test |
| Performance | Bounded Node smoke exists | Not a sustained 12,500 fills/sec benchmark; add paced workloads and raw results |
| Kubernetes | Configuration validated only | No local cluster run yet; add Kind, dependency workloads, metrics, and failure tests |
| AWS | Prepared only | Missing documented public ingress/TLS/monitoring choices; no cluster deployment or cloud uptime proof |
| Security/ops | Demo credentials, browser localStorage | Suitable for isolated paper demo, not a brokerage; no rate limiting/MFA, no production SLO |

## Checklist

- [x] Audit source and capture baseline tests before editing.
- [x] Correct auth, invalid-order, idempotency, and misleading chart risks.
- [x] Add unit and real PostgreSQL/Kafka scenario evidence.
- [x] Run repeatable load and separate read/write database experiments.
- [x] Deploy and test local Kubernetes, including disruption/scaling.
- [x] Validate AWS configuration locally; never apply infrastructure.
- [x] Write reports, requested metrics table, interview guide, and limitations.

## Results by Phase

### Application Correctness

JWT authentication now respects frozen/disabled users and checks issuer/algorithm.
Missing authentication returns 401; authorization failures stay 403 and no longer
trigger an inappropriate frontend refresh. Refresh rows are locked during rotation.
Registration normalizes email; DB integrity conflicts return a safe 409 message.
Internal order inputs are validated; conflicting duplicate payloads get 409.

The sine-wave performance generator, synthetic stock curve and watchlist curve
were removed. Real sessions chart observed equity; only explicit offline demo mode
uses sample data. Account cache invalidation now includes account summary. Live
sessions no longer merge hardcoded demo stocks into real API stock lists.
Legacy dashboard DTO names dailyProfit/monthlyProfit remain for compatibility,
but their card labels now accurately say unrealized/realized P/L. No durable
historical NAV table or genuine daily/monthly return reconstruction was invented.

### Tests and Database

Latest full Maven run: 28 unit + 11 PostgreSQL integration tests, all passed,
zero failed/skipped. PostgreSQL concurrency tests prove buy/sell serialization,
duplicate handling and transaction rollback. V6 adds new constraints/indexes without
modifying V1-V5. The clean DB has 14 application tables plus Flyway metadata.
Real Kafka recovery scenario passed: stopped-worker backlog, later fill, duplicate
broker replay, one ledger/debit, malformed-message DLT, zero final partition lag.
Exact transient retry timing and broker failover remain untested.

### Measurements and Infrastructure

Raw bounded runs: 248 and 176 acknowledged/filled orders; actual observed fills/sec
including drain were 19.66 and 10.52. Higher run overlapped an API rollout; these are
not clean capacity comparisons. Lower-rate injector saturation made further
12,500-target runs unjustified on this laptop. The target remains unverified.

Repeated temporary-table SQL tests show faster indexed history reads and slower
inserts, not a universal 42% read/write reduction. Short availability scenarios
include failed status requests during rollout/scaling and initial disk exhaustion;
99.99% long-term uptime remains unverified.

Kind reached Ready, metrics-server supplied HPA data, rescale events were captured,
and API rolling update/worker replacement completed. The first non-root frontend
image hit a PID-file permissions error; it was fixed to /tmp and the corrected
pod reached Ready with no restart. Existing healthy frontend was retained during
the failed rollout. Local schema checks passed 17 resources; AWS overlay 13.
AWS wrapper init/validate passed; no plan/apply or cloud calls created resources.

## Remaining Risks / Unfinished Verification

- Real-browser mobile layout initially overflowed; grid sizing fixes now pass at
  390px. UI registration/buy/sell/watchlist/balance-refresh checks passed on Kind
  and Compose, with desktop/mobile screenshots and earlier failed runs retained.
- Local full-image Trivy failed while downloading its DB with disk/I/O errors.
  npm audit is clean after compatible lockfile updates; that is not a Java/image scan.
- First updated CI passed backend/frontend/Kafka/config checks but failed image
  scans. Compatible dependency/OS fixes were added; conditional Spring MVC
  advisories remain tracked in SECURITY_REVIEW.md. Failure gates are not suppressed.
- EKS/MSK/RDS/IAM/TLS and ALB controller need real authorized cloud validation.
- One broker/DB/Kind node, no long-term SLO, no node or consumer-lag autoscaling.
- Queue DLT failures lack a durable public receipt/status; 404 is ambiguous.
- Tokens in localStorage, seeded public demo credentials, custom JWT parser, no
  rate limiting/MFA: not a security-reviewed brokerage deployment.
- Provider contract/freshness/outage tests, persistent NAV history, broad frontend
  component/admin tests, backup/restore and longer isolated soak tests remain.
- V6 requires a legacy-data constraint preflight and backup before a live rollout.
  Hosted main/Render/Vercel have not been changed by this lab work.

See README and the linked performance, availability, DB, testing, AWS, interview
and claim-verification reports for exact commands/evidence and accurate wording.

The final isolated Compose stack (API, two workers, frontend, PostgreSQL and Kafka)
also passed the existing smoke/replay/concurrent-buying-power script with 20 fills.
It observed 21.6 acknowledgements/sec and 5.4 fills/sec during a concurrent browser
run; this is smoke evidence, not capacity. Compose was stopped without deleting
its volumes and the Kind cluster restored. The final frontend build/lint passed;
bundle splitting remains a performance improvement (about 795 kB uncompressed JS).
