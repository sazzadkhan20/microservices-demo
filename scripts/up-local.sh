#!/usr/bin/env bash
# Start full local platform: infra + all microservices + MinIO (+ optional frontend build).
# Large-scale note: each service keeps its own Dockerfile for independent deploy;
# Compose profiles are for local/dev convenience only.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f "$ROOT/.env" ]]; then
  echo "ERROR: missing .env — copy .env.example and set secrets:"
  echo "  cp .env.example .env"
  exit 1
fi

echo "==> Syncing RabbitMQ definitions from .env credentials..."
node "$ROOT/scripts/gen-rabbitmq-definitions.mjs"

echo "==> Syncing frontend .env secrets from root .env..."
node "$ROOT/scripts/sync-frontend-env.mjs"

echo "==> Starting Redis, RabbitMQ, Postgres hosts, MinIO, and all apps..."
# RabbitMQ + MinIO are core infra (always on) — messaging + document storage.
# Secrets come only from `.env` (see .env.example).
docker compose --profile apps up -d --build "$@"

echo "==> Waiting for Gateway :8090 ..."
for i in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:8090/actuator/health" >/dev/null 2>&1; then
    echo "Gateway is up."
    break
  fi
  sleep 2
  if [[ "$i" -eq 60 ]]; then
    echo "WARN: Gateway not healthy yet — check docker compose logs"
  fi
done

echo "==> Provision / upgrade Qii Creative tenant..."
bash "$ROOT/scripts/provision-qii.sh" || true

echo ""
echo "Ready:"
echo "  Gateway Swagger  http://127.0.0.1:8090/swagger-ui.html"
echo "  Frontend         http://127.0.0.1:3000"
echo "  MinIO console    http://127.0.0.1:9001  (credentials from .env MINIO_ROOT_*)"
echo "  RabbitMQ UI      http://127.0.0.1:15672  (credentials from .env RABBITMQ_*)"
echo ""
echo "Login: account picker → pick Asman Rahman (SUPERADMIN)"
