#!/usr/bin/env bash
# Start full local platform: infra + all microservices + MinIO (+ optional frontend build).
# Large-scale note: each service keeps its own Dockerfile for independent deploy;
# Compose profiles are for local/dev convenience only.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Starting Redis, Postgres hosts, MinIO, and all apps..."
docker compose --profile apps --profile minio up -d --build "$@"

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
echo "  MinIO console    http://127.0.0.1:9001  (minioadmin/minioadmin)"
echo ""
echo "Login: account picker → pick Asman Rahman (SUPERADMIN)"
