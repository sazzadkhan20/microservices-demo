#!/usr/bin/env bash
# Start only Redis + 3 Postgres hosts (apps run via Gradle bootRun).
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d redis postgres-registry postgres-hrms postgres-payroll
docker compose ps
echo "Infra ready. Next: ./gradlew :tenant-registry:bootRun (and other services)."
