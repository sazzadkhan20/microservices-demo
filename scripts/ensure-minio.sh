#!/usr/bin/env bash
# Recreate/start MinIO and verify live + credentials match .env (file-service keys).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f "$ROOT/.env" ]]; then
  echo "ERROR: missing .env"
  exit 1
fi
set -a
# shellcheck disable=SC1091
. "$ROOT/.env"
set +a

if [[ -z "${MINIO_ROOT_USER:-}" || -z "${MINIO_ROOT_PASSWORD:-}" ]]; then
  echo "ERROR: MINIO_ROOT_USER / MINIO_ROOT_PASSWORD required in .env"
  exit 1
fi
if [[ "${MINIO_ACCESS_KEY:-}" != "$MINIO_ROOT_USER" || "${MINIO_SECRET_KEY:-}" != "$MINIO_ROOT_PASSWORD" ]]; then
  echo "ERROR: MINIO_ACCESS_KEY/SECRET_KEY must equal MINIO_ROOT_USER/ROOT_PASSWORD"
  exit 1
fi
if [[ ${#MINIO_ROOT_PASSWORD} -lt 8 ]]; then
  echo "ERROR: MINIO_ROOT_PASSWORD must be at least 8 characters"
  exit 1
fi

echo "==> Starting MinIO..."
docker compose up -d minio

echo "==> Waiting for health..."
for i in $(seq 1 36); do
  if curl -sf "http://127.0.0.1:9000/minio/health/live" >/dev/null 2>&1; then
    echo "MinIO API healthy on :9000"
    echo "Console: http://127.0.0.1:9001  (user=$MINIO_ROOT_USER)"
    exit 0
  fi
  sleep 2
done

echo "ERROR: MinIO did not become healthy — docker compose logs minio"
docker compose logs --tail 80 minio || true
exit 1
