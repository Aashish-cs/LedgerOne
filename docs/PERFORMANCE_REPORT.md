# Performance Report

## Status

**12,500+ completed mock transactions/sec remains an UNVERIFIED target.**
An HTTP 202 or Kafka acknowledgement is not a completed trade.

## Environment

Mac14,7 with 8 GiB physical RAM. Colima Linux/aarch64 VM: 4 virtual CPUs,
6,197,452,800 bytes memory. Single-node Kind v0.33.0, Kubernetes v1.35.0,
PostgreSQL 16, Apache Kafka 3.9.1, six partitions, one broker, controlled seeded
quotes, no Finnhub calls. Java 21; Node v26.4.0 for the load generator.
API and worker JVM heaps are capped at 256 MiB in the local cluster.
This is a shared laptop, not dedicated load-testing hardware.

## Commands

```bash
bash tests/performance/local-kubernetes.sh up
bash tests/performance/local-kubernetes.sh forward
# In another terminal, with the frontend proxy reachable at loopback:
BENCH_URL=http://127.0.0.1:18090 RATE=100 SECONDS=5 ACCOUNTS=8 \
  KUBECTL=kubectl node tests/performance/load.mjs
```

Set RATE to 100, 1000, 5000, 10000, or 12500 to select a target, not a measured
result. The harness caps concurrency and counts dropped scheduling slots rather
than accumulating unlimited requests. Fix lower-rate failures before raising load.

## Measurement Model

- Sent requests/sec and scheduled-but-dropped slots describe the injector.
- HTTP 202 includes broker partition/offset metadata, so acknowledged publications
  are counted separately from status-200 reads and from database writes.
- Persistence is verified by reading each user's paginated order history.
- The report includes HTTP p50/p95/p99, response/error counts, persisted order states,
  and first-observed persistence latency (which includes polling).
- KUBECTL enables CPU/memory samples and consumer-group offset/lag snapshots. The
  frontend reverse proxy sends traffic to the API's ClusterIP Service; directly
  port-forwarding one API pod would not exercise service load balancing.
- Consumer drain contributes to elapsed completed-fill throughput.

## Observations and Failures

Recovered runs used eight fresh accounts, 0.01 AAPL shares per order, normal fees,
and an in-flight cap of 64. Registration happens before the measured interval.

| Target/sec; submission duration | Sent / acknowledged / filled | Sent/sec | Broker acks/sec including HTTP drain | Fills/sec including persistence drain | HTTP p50 / p95 / p99 ms | Dropped scheduling slots |
| --- | --- | ---: | ---: | ---: | --- | ---: |
| 100; 5 s | 248 / 248 / 248 | 49.6 | 39.72 | 19.66 | 1247.09 / 2055.06 / 2520.18 | 251 |
| 1,000; 5 s | 176 / 176 / 176 | 35.2 | 22.57 | 10.52 | 2159.45 / 4578.88 / 5209.52 | 4,821 |

[100/sec target raw run](../tests/performance/results/load-100-369d4444-a3e4-4ea9-a524-0a769011295e.json),
[1,000/sec target raw run](../tests/performance/results/load-1000-4be4be65-0710-4b29-aaa7-f920f6ef4032.json).
Both had zero HTTP failures **among sent requests**. Dropped slots are excluded from
that error-rate denominator and are material missed load, not successes. The
five-second scheduler also omits its final fractional batch at the deadline.
Observed end-to-end p95 was 10,058.82 ms and 15,286.10 ms respectively, including
polling. Total measured durations including drain were 12.615 s and 16.737 s.

The higher-target run overlapped an API rolling restart and short availability
sampling. It is a disruption observation, not an isolated apples-to-apples capacity
comparison. No 5,000/10,000/12,500 run was attempted after lower-rate saturation:
the configured generator can select these targets, but they are unverified.
There is no credible sustained transactions/sec capacity claim from these runs.
Raw telemetry includes per-pod CPU/memory and six-partition lag snapshots.

The initial 100/sec attempt timed out during fixture creation while the host disk
had 112 MiB free. Docker reported I/O errors while extracting the vulnerability
database. [Failure record](../tests/performance/results/fixture-failure.txt).
No throughput value is assigned to that failed attempt.

The original bounded smoke benchmark in [distributed-trading.md](distributed-trading.md)
is historical evidence only: 100 orders, 41.3 broker acknowledgements/sec and 14.6
verified fills/sec on a different 2-CPU/4-GiB local run. It is not a sustained
capacity result for this revision.

Current raw runs are retained in [tests/performance/results](../tests/performance/results).
Do not substitute the configured target rate for actual sent/accepted/persisted rates.
No test in this report verifies 12,500 completed transactions/sec.

## Bottlenecks and Next Work

BCrypt registration is fixture cost, not trading throughput. Per-account row locks
intentionally serialize conflicting trades. Hibernate loads entities and related
data, records audit/notifications/risk per fill, and shares PostgreSQL with status
polling. Disk exhaustion invalidates capacity conclusions. Future work: dedicated
test hardware, stable resource headroom, longer steady-state runs, DB connection
and SQL profiling, bounded quote caches, and consumer-lag-based scaling.
