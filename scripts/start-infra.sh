#!/usr/bin/env bash
# Start local infrastructure only (apps run via Gradle bootRun or `docker compose --profile apps`).
# Redis = gateway session revocation; RabbitMQ = outbox/inbox messaging; MinIO = company documents.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d redis rabbitmq postgres-hrms postgres-payroll postgres-platform minio
docker compose ps
echo "Infra ready (Postgres :5433/:5434/:5436, RabbitMQ :5672, Redis :6379, MinIO :9000)."
echo "Next: ./gradlew :people-service:bootRun (each service migrates its own fixed database on startup)."
