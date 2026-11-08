#!/usr/bin/env bash
# Cut over legacy hrms_* tenant DBs → people_*/leave_*/timework_* and rewrite registry routing.
# Safe to re-run (idempotent). After success, optionally archive leftover hrms_* DBs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ADMIN_KEY="${PLATFORM_ADMIN_KEY:-local-platform-admin-change-me}"
REGISTRY="${REGISTRY_URL:-http://127.0.0.1:8080}"

echo "==> Waiting for registry at $REGISTRY ..."
for i in $(seq 1 60); do
  if curl -sf "$REGISTRY/api/registry/health-info" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -sf "$REGISTRY/api/registry/health-info" >/dev/null

echo "==> Running workforce DB split..."
curl -sf -X POST "$REGISTRY/api/registry/tenants/split-workforce" \
  -H "X-Platform-Admin-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json"
echo

echo "==> Databases on postgres-hrms (host port 5433):"
docker exec microservices-demo-postgres-hrms-1 psql -U platform -d postgres -c \
  "SELECT datname FROM pg_database WHERE datname ~ '^(people_|leave_|timework_|hrms_|archive_hrms_)' ORDER BY 1;"

echo "==> Registry routing (workforce services):"
docker exec microservices-demo-postgres-registry-1 psql -U platform -d registry_db -c \
  "SELECT service_name, tenant_id, regexp_replace(jdbc_url, '^.*://', '') AS db
   FROM tenant_routing
   WHERE service_name IN ('people','leave','time-work','hrms')
   ORDER BY tenant_id, service_name;"
