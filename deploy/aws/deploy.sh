#!/usr/bin/env bash
set -euo pipefail

if [[ ${1:-} != --deploy || $# != 1 ]]; then
  printf '%s\n' 'Usage: bash deploy/aws/deploy.sh --deploy' \
    'Deploys the current clean Git revision to an already-provisioned LedgerOne AWS sandbox.' >&2
  exit 2
fi

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
infra="$root/deploy/aws"
if [[ -n $(git -C "$root" status --porcelain) ]]; then
  printf '%s\n' 'Commit local changes before building immutable deployment images.' >&2
  exit 2
fi

output() { terraform -chdir="$infra" output -raw "$1"; }
region=$(output region)
account=$(output aws_account_id)
cluster=$(output cluster_name)
namespace=$(output namespace)
host=$(output database_host)
database=$(output database_name)
repositories=$(terraform -chdir="$infra" output -json ecr_repository_urls)
tag=$(git -C "$root" rev-parse HEAD)

actual_account=$(aws sts get-caller-identity --region "$region" --query Account --output text)
if [[ $actual_account != "$account" ]]; then
  printf '%s\n' 'Active AWS identity does not match the Terraform deployment account.' >&2
  exit 2
fi

umask 077
temp=$(mktemp -d)
export KUBECONFIG="$temp/kubeconfig"
cleanup_bootstrap=false
k() { kubectl --namespace "$namespace" "$@"; }
cleanup() {
  if [[ $cleanup_bootstrap == true ]]; then
    k delete job ledgerone-db-bootstrap --ignore-not-found --wait=false >/dev/null || true
    k delete secret ledgerone-db-bootstrap --ignore-not-found >/dev/null || true
  fi
  rm -rf "$temp"
}
trap cleanup EXIT

aws eks update-kubeconfig --region "$region" --name "$cluster" --kubeconfig "$KUBECONFIG" >/dev/null
kubectl create namespace "$namespace" --dry-run=client -o yaml | kubectl apply -f -

backend=$(jq -er .backend <<<"$repositories")
frontend=$(jq -er .frontend <<<"$repositories")
aws ecr get-login-password --region "$region" |
  docker login --username AWS --password-stdin "${backend%%/*}"

publish() {
  local repository=$1 directory=$2
  shift 2
  if ! aws ecr describe-images --region "$region" --repository-name "${repository#*/}" \
      --image-ids "imageTag=$tag" >/dev/null 2>&1; then
    docker buildx build --platform linux/arm64 --push --tag "$repository:$tag" "$@" "$root/$directory"
  fi
}
publish "$backend" backend
publish "$frontend" frontend --build-arg VITE_API_BASE_URL=/api --build-arg VITE_QUEUED_ORDERS=true

curl --fail --silent --show-error --location \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o "$temp/rds-ca.pem"
k create configmap ledgerone-rds-ca --from-file="rds-ca.pem=$temp/rds-ca.pem" --dry-run=client -o yaml | k apply -f -

# Preserve JWT and database credentials across ordinary image releases.
if [[ -z $(k get secret ledgerone-secrets --ignore-not-found -o name) ]]; then
  if [[ -n $(k get deployment ledgerone-api --ignore-not-found -o name) ]]; then
    printf '%s\n' 'Existing API is missing ledgerone-secrets. Recover the secret before deploying.' >&2
    exit 2
  fi
  credentials=$(aws secretsmanager get-secret-value --region "$region" \
    --secret-id "$(output database_master_secret_arn)" --query SecretString --output text)
  db_password=$(openssl rand -hex 32)
  cleanup_bootstrap=true
  {
    printf 'PGHOST=%s\nPGDATABASE=%s\n' "$host" "$database"
    printf 'PGUSER=%s\n' "$(jq -er .username <<<"$credentials")"
    printf 'PGPASSWORD=%s\nAPP_PASSWORD=%s\n' "$(jq -er .password <<<"$credentials")" "$db_password"
  } | k create secret generic ledgerone-db-bootstrap --from-env-file=/dev/stdin
  unset credentials
  k create -f "$infra/bootstrap-db.yaml"
  k wait --for=condition=complete job/ledgerone-db-bootstrap --timeout=130s
  {
    printf 'DATABASE_USERNAME=ledgerone_app\nDATABASE_PASSWORD=%s\n' "$db_password"
    printf 'JWT_SECRET=%s\n' "$(openssl rand -hex 32)"
    if [[ -n ${FINNHUB_API_KEY:-} ]]; then printf 'FINNHUB_API_KEY=%s\n' "$FINNHUB_API_KEY"; fi
  } | k create secret generic ledgerone-secrets --from-env-file=/dev/stdin
  unset db_password
fi

jdbc="jdbc:postgresql://$host:5432/$database?sslmode=verify-full&sslrootcert=/etc/ledgerone/rds/rds-ca.pem"
k create configmap ledgerone-config \
  --from-literal="DATABASE_JDBC_URL=$jdbc" \
  --from-literal="KAFKA_BOOTSTRAP_SERVERS=$(output kafka_bootstrap_servers)" \
  --from-literal="KAFKA_ORDER_TOPIC=$(output kafka_order_topic)" \
  --from-literal="KAFKA_ORDER_GROUP=$(output kafka_order_group)" \
  --from-literal="AWS_REGION=$region" \
  --from-literal="AWS_EC2_METADATA_DISABLED=true" \
  --from-literal="ALLOWED_ORIGINS=${ALLOWED_ORIGINS:-http://127.0.0.1:18080,http://localhost:18080}" \
  --from-literal="MARKET_LIVE_PRICES_ENABLED=${MARKET_LIVE_PRICES_ENABLED:-true}" \
  --dry-run=client -o yaml | k apply -f -

for role in api worker; do
  k create serviceaccount "ledgerone-$role" --dry-run=client -o yaml | k apply -f -
  k annotate serviceaccount "ledgerone-$role" --overwrite "eks.amazonaws.com/role-arn=$(output "${role}_role_arn")"
done

mkdir "$temp/manifests" "$temp/release"
cp -R "$root/deploy/kubernetes/." "$temp/manifests/"
jq -n --arg backend "$backend" --arg frontend "$frontend" --arg tag "$tag" '{
  apiVersion: "kustomize.config.k8s.io/v1beta1", kind: "Kustomization",
  resources: ["../manifests/aws"],
  images: [
    {name: "ledgerone-backend", newName: $backend, newTag: $tag},
    {name: "ledgerone-frontend", newName: $frontend, newTag: $tag}
  ]
}' > "$temp/release/kustomization.yaml"
kubectl kustomize "$temp/release" | k apply -f -

for deployment in ledgerone-api ledgerone-order-worker ledgerone-market-scheduler ledgerone-frontend; do
  k rollout status "deployment/$deployment" --timeout=300s
done
k get pods,svc,hpa
