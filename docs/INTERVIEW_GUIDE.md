# LedgerOne Interview Guide

Learn the flow first. You do not need to memorize every filename or formula.
These answers describe the lab branch; the public demo still runs synchronous
trading on Render/Vercel. Do not describe local Kubernetes as an EKS deployment.

## 1. Tell me about LedgerOne

"LedgerOne is a paper-trading app. Users get $100,000 in virtual money, search
stocks, place buy and sell orders, and track holdings and past trades. I used
React, Spring Boot and PostgreSQL, then added a separate Kafka processing path."

## 2. Why did you build it?

"I wanted to learn backend rules that have to stay correct: checking balances,
updating holdings, preventing duplicates, and handling two requests at once.
Paper trading made those problems concrete without handling real money."

## 3. Why Java and Spring Boot?

"Spring Boot gives me REST controllers, validation, security, database access and
transactions in one framework. Java's BigDecimal is useful for money calculations."

## 4. Why Kafka?

"It separates accepting orders from executing them. The API can acknowledge a
broker write while workers process the backlog. It adds eventual consistency and
operations work, so I kept the simpler synchronous path too."

## 5. What happens when a user submits an order?

"The API checks authentication, request shape and account ownership. In queue mode
it publishes with the user ID as the key and waits for Kafka's acknowledgement.
The worker locks the user's database row, checks funds or shares, then commits
the order, cash, holdings and ledger together. The UI polls for the result."

Code to study: `QueuedOrderController`, `QueuedOrderConsumer`, `TradingService`.

## 6. Why consumer groups?

"Workers in one group share partitions, so they split the work. Events for a user
have the same key and stay ordered in one partition. Adding more than six active
consumers cannot help a six-partition topic."

## 7. How did you test throughput?

"I used a paced Node load generator against the local Kubernetes Service, recorded
HTTP latency and acknowledgements, then read PostgreSQL-backed order history to
count real fills. I also sampled CPU, memory and consumer lag."

## 8. What does 12,500 transactions/sec mean?

"That is an unverified target, not my measured result. The recovered 100/sec target
run actually sent 49.6 requests/sec and observed about 19.66 fills/sec including
drain. The laptop and bounded injector saturated well below the target."

Do not call a broker receipt a completed transaction or present this short run as
steady-state capacity. See `PERFORMANCE_REPORT.md`.

## 9. How do you prevent duplicate trades?

"The frontend sends a client order ID. Under the user's row lock, the service looks
up that user's ID. An exact retry returns the existing order; a different payload
with that ID gets a conflict. A database unique key adds another guard."

## 10. What happens if Kafka crashes?

"Without an acknowledgement the API returns 503, not success. The caller retries
with the same client order ID, because a timed-out publish may still have reached
Kafka. Workers can replay after recovery without applying a trade twice. Our
single-broker local setup is not broker-high-availability."

## 11. How do you protect balances?

"I check cash including fees before a buy and shares before a sell. Cash, holdings,
the order and the ledger commit in one Spring transaction. If any step fails,
the changes roll back together. Pending limit orders do not deduct money."

## 12. How do you prevent races?

"I lock the user's database row before reading or changing trading state. Another
trade for that user waits, then sees the committed balance. READ COMMITTED alone
would not prevent double spending. I tested simultaneous buys, sells and duplicates
against PostgreSQL, not just mocks."

## 13. Why PostgreSQL?

"Orders, holdings and ledger records have clear relationships. PostgreSQL gives me
foreign keys, unique constraints, exact numeric columns, row locks and transactions."

## 14. Why Flyway?

"It versions database changes with the code. A clean integration-test database
applies V1 through V6 and Hibernate validates the resulting schema. I add a new
migration instead of editing a migration that has already run."

## 15. How did you optimize queries?

"I measured recent-order queries with EXPLAIN ANALYZE on 300,000 synthetic rows,
then compared composite indexes across 20 repetitions. I added the portfolio
history index to match the actual endpoint. Extra indexes also make inserts slower."

## 16. How did you measure 42%?

"I could not reproduce an overall 42% read/write improvement. The isolated indexed
portfolio-history query improved from a 16.284 ms median to 0.105 ms, but the
100-row insert batch got slower. I report those separately, not as one percentage."

## 17. How does autoscaling work?

"Metrics Server provides CPU readings. HPA compares CPU use with the requested CPU
and adjusts replica count toward a 65% target, within configured limits. I observed
local rescale events. Consumer lag would be a better additional worker signal."

## 18. What are readiness and liveness probes?

"Readiness decides whether a pod should receive traffic. Liveness decides whether
Kubernetes should restart it. Startup probes give the Java app time to start before
liveness checks begin. Our readiness includes the database, not full trade delivery."

## 19. Did you deploy to AWS EKS?

"Not the complete application. The live demo uses Render and Vercel. I prepared and
locally validated Terraform and EKS configuration, and ran the app on local Kind.
AWS IAM, ingress, MSK and RDS still need a real cloud deployment test."

## 20. How did you test infrastructure locally?

"I built the images, loaded them into Kind, started PostgreSQL, Kafka and the app,
then tested probes, worker restarts, duplicate replay, DLT, a rolling update and
autoscaling. The retained logs include failures as well as successful checks."

## 21. How did you measure availability?

"I polled a status endpoint during short local scenarios and recorded both request
success and approximate outage time. Normal operation and worker restart had no
failed samples in recovered runs; rollout and scaling had failures. That does not
establish 99.99% uptime or that trades were always completing."

## 22. Biggest technical challenge?

"Keeping balances correct across retries and simultaneous requests. I combined
user-row locking, transaction rollback and client-order idempotency, then tested
them with concurrent calls to real PostgreSQL. Kafka replay adds a second reason
why a trade must be safe to process more than once."

Only describe this as your personal learning after you understand and can trace
the code. Be candid about significant Codex assistance if asked how it was built.

## 23. What would you improve next?

"A durable queued-order receipt including DLT failure status, a longer load-test
window, lag-based scaling, historical account snapshots, rate limiting and stronger
session handling. Then I would validate the AWS deployment with a fixed budget."

## A Two-Minute Walkthrough to Practice

Open these files one at a time:

1. `backend/src/main/java/com/ledgerone/LedgerOneApplication.java`: starts Spring.
2. `controller/TradingController.java`: synchronous request entry point.
3. `orderqueue/QueuedOrderController.java`: broker acknowledgement and receipt.
4. `orderqueue/QueuedOrderConsumer.java`: event to trading service.
5. `service/TradingService.java`: lock, validate, fill/reject, commit.
6. `repository/UserAccountRepository.java`: actual `PESSIMISTIC_WRITE` lock.
7. `TradingPostgresIT.java` in test sources: prove two buys cannot double spend.
8. `frontend/src/api/client.ts`: API request and result polling.

Parent/child classes are not the same as these layers. A service using a repository
is composition/dependency injection, not inheritance. Interviewers may ask to see
code; explaining one real request clearly is more useful than memorizing folders.
