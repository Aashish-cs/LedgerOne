# Testing

## Recorded Result

The November 5 audit run passed **28 unit tests and 11 PostgreSQL integration tests**:
39 total, 0 failures, 0 errors, 0 skipped. This exceeds the original 18-test resume
claim; it does not mean every test is a distributed-system integration test.
[Raw Maven log](../tests/performance/results/maven-tests.txt).

## Reproduce

Use Java 21 and the committed Maven wrapper. Unit tests do not require Docker:

```bash
cd backend
./mvnw test
```

The integration profile requires a working Docker daemon and pulls PostgreSQL 16:

```bash
cd backend
./mvnw -Pintegration verify
```

For Colima on this Mac, the recorded command was:

```bash
DOCKER_HOST=unix:///Users/ashishmishra/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw -B -Pintegration verify
```

Inspect `backend/target/surefire-reports` and `backend/target/failsafe-reports`.
The latter is run by Failsafe only with `-Pintegration`; plain `test` is not the
full integration suite. CI uploads both report directories even after failure.

## Unit Coverage

Files are under `backend/src/test/java/com/ledgerone/`:

| File | What is checked |
| --- | --- |
| `service/TradingServiceTest.java` | Exact duplicate returns original, insufficient funds, cash/holding/ledger changes on buy, no debit for waiting limit order, rechecking cash/shares, cancelled order cannot refill |
| `service/PortfolioServiceTest.java` | Legacy allocation paths plus rejection of a second active account and account deletion |
| `orderqueue/QueuedOrderTest.java` | Acknowledged partition/offset in 202, asynchronous and immediate broker errors become 503, frozen account does not publish, invalid event rejected, valid event dispatches to trading |
| `service/StockQuoteServiceTest.java` | Provider parsing, cache and failure behavior |
| `service/RiskCalculatorTest.java` | Rule-based concentration/diversification score |
| `security/JwtServiceTest.java` | Signed-token identity/role round trip |
| `validation/TickerSymbolValidatorTest.java` | Ticker input constraints |
| `config/RenderDatabaseUrlEnvironmentPostProcessorTest.java` | Render URL to JDBC conversion |
| `LedgerOneApplicationTests.java` | Entry-point smoke assertion, not a full application-context integration test |

Mocks verify business decisions without proving PostgreSQL locking or Kafka delivery.

## Real PostgreSQL Coverage

`TradingPostgresIT.java` starts a disposable PostgreSQL 16 container and the real
Spring application with MockMvc. It verifies:

1. All six Flyway migrations apply and create 14 business tables.
2. Concurrent buys cannot spend the same cash twice.
3. Concurrent sells cannot sell the same shares twice.
4. Concurrent duplicate requests yield one order and one ledger record.
5. A reused ID with a different quantity is rejected.
6. Failure after cash mutation rolls back cash, order, and ledger changes.
7. A negative quantity never changes cash.
8. Login, refresh rotation, and rejection of a replayed refresh token.
9. Frozen users cannot use existing JWTs or refresh tokens.
10. Anonymous requests receive 401, user-to-admin access receives 403, invalid order receives 400.
11. Registration creates exactly one account; duplicate email is rejected.

The risk service is mocked for deterministic rollback injection. These tests do not
call Finnhub and do not prove behavior during a real market-provider outage.

## Real Kafka / Kubernetes Evidence

```bash
KUBECTL=kubectl node tests/performance/kafka-recovery.mjs
```

This intentionally disrupts only the disposable `ledgerone-audit` Kind cluster.
The recorded run verified: broker acceptance with workers stopped, later fill after
restart, two duplicate broker replays still giving one ledger entry/debit, malformed
JSON in the dead-letter topic, and final consumer lag zero on all six partitions.
[Raw evidence](../tests/performance/results/kafka-recovery-2d21682d-555e-42b5-b5e7-5bf816d18724.json).
These are scenario assertions, not additional JUnit cases. The existing
`benchmarks/queued-orders.mjs` runs a separate Compose smoke/replay check in CI.

Retry policy is configured in `OrderQueueConfig.java` (two retries, one-second
backoff for retryable failures). Malformed messages skip retries. An injected
transient database outage proving the exact retry sequence remains a coverage gap.

## Frontend, Configuration and Security

```bash
cd frontend
npm ci
npm run lint
npm run build
npm audit --omit=dev --audit-level=high
```

Compatible lockfile updates removed the initially reported npm vulnerabilities;
the local audit reported zero afterward. This is not a clean Java/container scan.
The local Trivy database download failed with disk/I/O errors; its failure log is
retained in `tests/performance/results/container-scan.txt`. CI scans both images
and fails on HIGH/CRITICAL findings. The first remote run passed four jobs but
failed both image scans; compatible fixes were added afterward. See
[security findings and remaining Spring upgrade work](SECURITY_REVIEW.md).

Kustomize rendering, live Kind schema validation, Terraform init/validate, shell
syntax checks, load/availability scripts, and browser evidence are described in the
[local Kubernetes report](LOCAL_KUBERNETES.md) and [performance report](PERFORMANCE_REPORT.md).

## Browser and Compose Checks

The local Playwright 1.63.0/Chromium harness `tests/browser/smoke.mjs` passed five
scenarios against both the Kind proxy and isolated Compose frontend: UI
registration with $100K, a queued buy with visible balance refresh, a sell with
cash/holdings verification, persisted watchlist add/remove, and 390px mobile
rendering without page overflow/uncaught JavaScript errors. These are five scenario
assertions, not five additional JUnit cases. Raw JSONs and real desktop/mobile
screenshots live in `tests/browser/results`; failed pre-fix runs are retained.

To use a separate Playwright install without changing application dependencies:

```bash
npm install --prefix /tmp/ledgerone-browser --no-save playwright@1.63.0
/tmp/ledgerone-browser/node_modules/.bin/playwright install chromium
PLAYWRIGHT_MODULE=/tmp/ledgerone-browser/node_modules/playwright/index.mjs \
  node tests/browser/smoke.mjs
```

The default URL is loopback:18090; `BROWSER_URL` can select the isolated Compose
frontend on :18092. The test intentionally creates disposable paper users/trades.

The final Compose smoke passed with 20 fills, correct ledger/cash, duplicate replay,
pending limit and concurrent buying-power checks. Raw result:
`tests/performance/results/compose-smoke.json`. It overlapped browser checks, so
21.6 acknowledgements/sec and 5.4 observed fills/sec are not capacity results.

```bash
docker build -t ledgerone-audit:local backend
docker build --build-arg VITE_QUEUED_ORDERS=true -t ledgerone-audit-frontend:local frontend
docker compose -p ledgerone-audit-compose -f docker-compose.yml -f docker-compose.queue.yml \
  -f tests/performance/compose.audit.yml up -d --no-build --scale worker=2 backend worker frontend
# Wait for :18093/actuator/health/readiness, then:
LEDGERONE_BENCH_URL=http://127.0.0.1:18093 ORDERS_PER_ACCOUNT=5 node benchmarks/queued-orders.mjs
docker compose -p ledgerone-audit-compose -f docker-compose.yml -f docker-compose.queue.yml \
  -f tests/performance/compose.audit.yml down
```

This Mac used the standalone `docker-compose` binary because the Docker CLI
plugin was not registered; the CI runner uses `docker compose`. `down` here does
not remove volumes. Do not run Kind and Compose simultaneously on a memory- or
disk-constrained laptop. No existing application/database stack was targeted.

## Important Remaining Coverage

- Concurrent refresh replay and concurrent registration HTTP races.
- Live provider contracts, stale-market/fallback cases, rate limits and outages.
- Broker loss, leader failover, commit-before-offset crash, DLT replay operations.
- Long steady-state soak and memory leak tests, multi-node failures, backups/restore.
- Frontend component/unit tests and broad admin/accessibility regression tests.
- Exact transient Kafka retry timing and production image vulnerability results.
- Real AWS IAM, network, TLS, MSK, RDS and EKS behavior; none is proved by mocks.
