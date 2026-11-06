# Availability Report

**99.99% long-term uptime is UNVERIFIED.** This report measures local observations,
not an AWS EKS service-level result.

The harness `tests/performance/availability.mjs` polls the public
`/api/system/status` endpoint through the frontend reverse proxy every roughly
100 ms with an 800 ms request timeout. Successful reads do not prove that orders
are completing. It records request success rate separately from a sample-weighted
time-availability estimate. An outage is approximated between sample boundaries;
it is not continuously measured time.

```bash
BENCH_URL=http://127.0.0.1:18090 ACTION=normal SECONDS=30 \
  KUBECTL=kubectl node tests/performance/availability.mjs
```

Supported actions: normal, restart-api, restart-worker, rollout, scale.
Disruptions run only against context `kind-ledgerone-audit`, namespace
`ledgerone-local`, and occur at five seconds. Inspect recovery, readiness,
replica count, and Kafka lag separately. The cluster has one broker and one database;
it is not a high-availability data tier.

Initial normal-operation run, 20 seconds: 115 attempted reads, 111 successful,
4 failed (96.5217% request success), approximately 3,626 ms sample-weighted
unavailability. The host disk exhausted during a concurrent scan and the first
load attempt timed out during fixture setup. This is an observed resource failure,
not a normal production SLO or a result to hide.

[Raw observations and events](../tests/performance/results/availability-normal.json).
Later scenarios are recorded separately in the same results directory.

## Recovered Short Scenarios

Each scenario below lasted 20 seconds. Files have unique run IDs so a later run
cannot overwrite earlier failures.

| Scenario | Successful / attempted | Request success | Approx. unavailable ms | Approx. sampled time availability |
| --- | ---: | ---: | ---: | ---: |
| Normal, recovered runtime | 131 / 131 | 100% | 0 | 100% |
| API rolling restart, concurrent bounded load | 94 / 97 | 96.91% | 2,861.53 | 85.69% |
| All worker pods deleted/recreated | 117 / 117 | 100% | 0 | 100% |
| API scale request to three replicas | 112 / 114 | 98.25% | 1,833.72 | 90.83% |

The last scale request was issued when the API already had three replicas, so it
does not prove a replica-count change. Actual HPA rescale events are linked in
[LOCAL_KUBERNETES.md](LOCAL_KUBERNETES.md). Other host/build activity was present;
do not attribute every timeout solely to the named disruption.
The observation window for worker deletion can end before all workers recover:
the separate Kafka recovery test verifies a later completed fill.
Worker failure did not stop status reads, but it did temporarily prevent trade
processing. These are deliberately different health concepts.

Even a later short test with zero failed samples would not establish 99.99% uptime.
An EKS rollout, external time-based monitoring, and a meaningful observation window
remain required before an uptime claim can be made.
