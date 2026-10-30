#!/usr/bin/env bash
# Start Redis + Postgres hosts + MinIO (apps run via Gradle bootRun).
# MinIO is required for employee document uploads (file-service → bucket per tenant).
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d redis postgres-registry postgres-hrms postgres-payroll postgres-platform
docker compose --profile minio up -d minio
docker compose ps
echo "Infra ready (Redis, Postgres, MinIO :9000 / console :9001)."
echo "Next: ./gradlew :tenant-registry:bootRun (and other services)."
echo "If documents still fail: docker compose --profile minio up -d minio"
