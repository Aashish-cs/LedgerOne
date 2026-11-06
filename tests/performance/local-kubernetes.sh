#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root"
kind=${KIND:-kind}
kubectl=${KUBECTL:-kubectl}
case "${1:-}" in
  up)
    if ! "$kind" get clusters | grep -qx ledgerone-audit; then
      "$kind" create cluster --name ledgerone-audit --image kindest/node:v1.35.0 --wait 120s
    fi
    docker build -t ledgerone-audit:local backend
    docker build --build-arg VITE_QUEUED_ORDERS=true -t ledgerone-audit-frontend:local frontend
    "$kind" load docker-image ledgerone-audit:local ledgerone-audit-frontend:local --name ledgerone-audit
    "$kubectl" --context kind-ledgerone-audit apply -k deploy/kubernetes/local
    "$kubectl" --context kind-ledgerone-audit apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.8.0/components.yaml
    # Kind's kubelet certificate is local-only; never carry this flag into EKS.
    "$kubectl" --context kind-ledgerone-audit -n kube-system patch deployment metrics-server --type=json \
      -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
    for service in ledgerone-api ledgerone-order-worker ledgerone-frontend; do
      "$kubectl" --context kind-ledgerone-audit -n ledgerone-local rollout status "deployment/$service" --timeout=300s
    done
    ;;
  forward)
    "$kubectl" --context kind-ledgerone-audit -n ledgerone-local port-forward svc/ledgerone-frontend 18090:80
    ;;
  down)
    "$kind" delete cluster --name ledgerone-audit
    ;;
  *) printf '%s\n' 'Usage: bash tests/performance/local-kubernetes.sh up|forward|down' >&2; exit 2 ;;
esac
