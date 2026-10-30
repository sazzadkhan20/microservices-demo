#!/usr/bin/env bash
# Ensure MinIO is up and tenant file buckets exist (required for document uploads).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Starting MinIO..."
docker compose --profile minio up -d minio

echo "==> Waiting for MinIO health..."
for i in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:9000/minio/health/live" >/dev/null 2>&1; then
    echo "MinIO is healthy."
    break
  fi
  sleep 1
  if [[ "$i" -eq 30 ]]; then
    echo "MinIO did not become healthy on :9000" >&2
    exit 1
  fi
done

NET=$(docker inspect microservices-demo-minio-1 -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' | awk '{print $1}')
echo "==> Ensuring tenant buckets on network $NET..."
docker run --rm --network "$NET" --entrypoint //bin/sh minio/mc -c '
  mc alias set local http://minio:9000 minioadmin minioadmin >/dev/null &&
  mc mb -p local/t-qii-creative 2>/dev/null || true &&
  mc mb -p local/t-techshop-retail 2>/dev/null || true &&
  mc mb -p local/t-impit-limited 2>/dev/null || true &&
  mc ls local
'

echo "Done. Console: http://127.0.0.1:9001  (minioadmin / minioadmin)"
