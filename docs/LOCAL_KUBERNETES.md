# Local Kubernetes Validation

This is a real **local Kind cluster**, not AWS EKS. The current hosted app on
Render/Vercel is not this cluster. All test data is simulated.

## Run

Requires Docker, Kind, kubectl, Node and several GiB of free disk space. The audit
used Colima 4 CPUs/6 GiB on an 8 GiB Mac. Avoid running image scans and load tests
at the same time on a constrained machine.

```bash
bash tests/performance/local-kubernetes.sh up
bash tests/performance/local-kubernetes.sh forward
# open http://127.0.0.1:18090
```

Override `KIND` and `KUBECTL` with executable paths as needed. The `local` overlay
includes PostgreSQL/Kafka PVCs, topic initialization, API, worker, scheduler and
frontend. The simulator and external quote calls are disabled for reproducibility.
Local-only credentials must never be used in a public environment.

## What Was Observed

- PostgreSQL, Kafka, API, worker, scheduler and frontend reached Ready.
- API and worker Spring health probes succeeded. Startup probes allow JVM warmup;
  readiness includes the DB and excludes unready pods from Service endpoints.
- Metrics Server served CPU/memory samples. Local-only insecure kubelet TLS is used
  for Kind certificates; do not copy that flag to EKS.
- CPU HPA events recorded API growth to 3 and worker growth from 2 to 3 replicas.
  [Events](../tests/performance/results/hpa-events.yaml) and
  [HPA snapshot](../tests/performance/results/hpa-observation.yaml).
- API rolling restart completed with old replicas retained while replacements
  started. The availability sampler nevertheless saw failed requests; no zero-outage
  assertion is made. Default Deployment rolling-update policy was used.
- Worker deletion/replacement and scale-to-zero/recovery were exercised. Kafka
  retained a submitted order while workers were stopped and processed it afterward.
- Duplicate replay, DLT and final lag are recorded in the Kafka recovery result.
- Two bounded load runs persisted 248 and 176 filled orders respectively.
- The host initially ran out of disk space during a Trivy download. Docker I/O
  errors required restarting Colima; no existing database volumes were removed.

HPA uses CPU utilization divided by resource requests, target 65%, local range
1-3 replicas. Some scale events occurred during JVM startup and recovery: they do
not prove traffic alone caused scaling. CPU may miss a waiting Kafka backlog;
consumer-lag scaling with KEDA is future work. Six partitions cap active consumers
in this group at six, irrespective of additional replicas. Nodes do not autoscale.

## Inspect / Exercise

```bash
kubectl --context kind-ledgerone-audit -n ledgerone-local get pods,svc,hpa
kubectl --context kind-ledgerone-audit -n ledgerone-local top pods
kubectl --context kind-ledgerone-audit -n ledgerone-local rollout status deployment/ledgerone-api
KUBECTL=kubectl node tests/performance/kafka-recovery.mjs
ACTION=restart-worker KUBECTL=kubectl node tests/performance/availability.mjs
ACTION=rollout KUBECTL=kubectl node tests/performance/availability.mjs
ACTION=scale KUBECTL=kubectl node tests/performance/availability.mjs
```

The scale scenario requests three replicas; if three already exist it is a
no-op, not proof of another scaling event. Use retained HPA events for that proof.
The API status endpoint does not test end-to-end trading health, and database-only
readiness does not guarantee Kafka is available. The enqueue path returns 503 when
broker acknowledgement is unavailable.

## Stop / Clean Up

Stop the foreground port-forward with Ctrl+C. To remove only the disposable test
cluster **and its local test PVC data**, run:

```bash
bash tests/performance/local-kubernetes.sh down
```

No cleanup command targets Render, Vercel, AWS, or any pre-existing local database.
