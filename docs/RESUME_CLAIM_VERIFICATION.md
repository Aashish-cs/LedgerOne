# Resume Claim Verification

Audit scope: `distributed-trading-lab`, November 5, 2026. No resume file was changed.
Live Render/Vercel and future EKS infrastructure are distinct from local evidence.

## Bullet 1: Distributed Kafka Trading / 12,500+

Statement: "Engineered a distributed trading engine with a Java Spring Boot REST
API handling 12,500+ mock transactions/sec by decoupling order submission from
storage via Apache Kafka consumer groups."

- Code: `orderqueue/QueuedOrderController.java`, `QueuedOrderConsumer.java`,
  `OrderQueueConfig.java`, `service/TradingService.java`, `application.yml`.
- Unit checks: `orderqueue/QueuedOrderTest.java` validates acknowledged receipt and
  broker failure. DB checks: `TradingPostgresIT.java` validates replay/races/rollback.
- Real broker script: `KUBECTL=kubectl node tests/performance/kafka-recovery.mjs`.
- Benchmark: `RATE=100 SECONDS=5 KUBECTL=kubectl node tests/performance/load.mjs`.
- Actual: 248 acknowledged/finally filled orders, 39.72 broker acks/sec including
  HTTP drain, 19.66 first-observed fills/sec including persistence drain. The
  1,000/sec target run sent only 35.2/sec, observed 10.52 fills/sec, and overlapped
  an API rollout. Dropped slots are not successful requests.
- Status: Kafka separation/groups implemented and locally exercised;
  **12,500+ completed transactions/sec UNVERIFIED**.
- Limits: short shared-laptop windows, 64 in-flight requests, no sustained capacity
  conclusion, one broker/DB, no public durable queued/DLT receipt.

[Performance report](PERFORMANCE_REPORT.md) and retained raw runs show the counts
and percentiles. Do not equate 202, broker writes and PostgreSQL fills.

Suggested wording: "Added Kafka-backed order submission and a partitioned worker
group, with broker-acknowledged acceptance, replay-safe database writes and
reproducible local load/recovery tests."

## Bullet 2: Kubernetes (EKS) / 99.99%

Statement: "Orchestrated containerized microservices on a Kubernetes (EKS) cluster
with auto-scaling replicas and health probes, maintaining 99.99% uptime during
traffic surges."

- Code: backend/frontend Dockerfiles, Compose, `deploy/kubernetes/base`, `local`,
  `aws`, `infra/aws`, `deploy/aws`.
- Commands: local Kind helper, `availability.mjs`, `kafka-recovery.mjs`, `kubectl
  get hpa/events`, Terraform init/validate. See exact report commands.
- Actual: local probes/rolling restart and worker recovery exercised; HPA events
  show API scaling to 3 and worker scaling from 2 to 3. Some events occurred
  during startup/recovery, so traffic-only causation is not proved.
- Recovered 20-second normal read test: 131/131 success. Rolling restart with load:
  94/97 success and about 2,862 ms sampled unavailability. Initial disk-pressure
  run also had failures, retained rather than hidden.
- Status: **local Kubernetes VERIFIED**; **EKS runtime UNVERIFIED / not deployed**;
  **99.99% uptime UNVERIFIED**.
- Limits: profiles in one Java application, not independent domain microservices;
  single-node Kind, single data tier, short read-only availability endpoint; no
  meaningful long-term uptime observation. A prepared diagram is not deployment.

[Local report](LOCAL_KUBERNETES.md), [availability](AVAILABILITY_REPORT.md),
[future AWS runbook](AWS_EKS_DEPLOYMENT.md).

Suggested wording: "Containerized API, worker and frontend roles and validated
Kubernetes probes, CPU autoscaling, rolling updates and Kafka worker recovery on
local Kind; prepared Terraform for a future EKS/MSK/RDS deployment."

## Bullet 3: 14 Tables / 42% / 18 Tests

Statement: "Designed the database model for 14 PostgreSQL tables with Flyway
migrations, transaction isolation, and indexed queries that cut read/write
latency by 42%, with 18 JUnit 5/Mockito tests."

- Code: `entity`, `repository`, `service/TradingService.java`, Flyway V1-V6.
- Tests: `TradingPostgresIT.java` plus unit classes listed in `TESTING.md`.
- Commands: `cd backend && ./mvnw -Pintegration verify`; execute
  `tests/performance/database.sql` against the isolated test DB.
- Actual schema: **14 business tables VERIFIED**, 13 JPA entities plus a join
  table. Flyway metadata is a separate fifteenth table, not hidden in the count.
- Concurrency: READ COMMITTED **plus user-row pessimistic locks**, not isolation
  alone, prevents two buys spending the same balance. PostgreSQL races and
  rollback injection pass.
- Actual query experiment: 300,000 rows, 3 warmups, 20 repetitions per phase and
  workload. Portfolio-history median 16.284 -> 0.105 ms; insert batch
  0.041 -> 0.277 ms (slower). No overall 42% read/write reduction established.
- Actual tests: **28 unit + 11 PostgreSQL integration = 39 passing**, 0 failures,
  errors or skipped. The old 18-test count is surpassed, not the current total.
- Status: schema/tests/concurrency VERIFIED; **42% combined latency UNVERIFIED**.
- Limits: temporary-table experiment excludes WAL/network/replication, phases are
  sequential, writes pay index-maintenance cost; not a production speed claim.

[DB report and raw plans](DATABASE_OPTIMIZATION.md), [tests](TESTING.md),
[raw Maven log](../tests/performance/results/maven-tests.txt).

Suggested wording: "Modeled 14 Flyway-managed PostgreSQL tables; protected trade
balances with transactional row locking and idempotent order IDs, backed by 28 unit
and 11 real PostgreSQL integration tests."

## Study / Presentation Rule

Lead with the working product and the correctness mechanisms. Keep targets visible
as targets, identify local versus cloud tests, and say what remains to be measured.
Do not describe a short 100% read sample as 99.99% uptime or a fast indexed SELECT
as a universal read/write improvement. Use the [interview guide](INTERVIEW_GUIDE.md)
to practice one real request end to end.
