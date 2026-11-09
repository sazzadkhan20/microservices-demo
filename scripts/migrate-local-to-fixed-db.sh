#!/usr/bin/env bash
# One-time migration of an EXISTING local Docker environment from the old multi-tenant layout
# (tenant-registry + *_qii_creative databases + shared superuser `platform`) to the single-company
# fixed-connection layout used by docker-compose.yml.
#
#   1. renames the Qii databases to their fixed names (people_db, leave_db, ...)
#   2. creates <svc>_migrator / <svc>_app roles, hands each database + its objects to its migrator,
#      grants DML-only access to the app role (docker/postgres/service-db.sql)
#   3. copies documents from the old per-tenant bucket into FILE_STORAGE_BUCKET and rewrites storage keys
#   4. drops every non-Qii legacy database, the registry database/container and the shared `platform` role
#
# Idempotent: safe to re-run. Stop all application services first (open DB sessions are terminated).
# Fresh environments do NOT need this script — first-boot init (docker/postgres/init-service-dbs.sh) does it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a; . ./.env; set +a

PROJECT="${COMPOSE_PROJECT_NAME:-microservices-demo}"
OLD_BUCKET="${OLD_FILE_BUCKET:-t-qii-creative}"
NEW_BUCKET="${FILE_STORAGE_BUCKET:-qii-hrms-documents}"

pg() { local host="$1"; shift; docker exec -i "${PROJECT}-${host}-1" psql -v ON_ERROR_STOP=1 -U postgres -qtA "$@"; }

rename_db() {
  local host="$1" from="$2" to="$3"
  if [[ "$(pg "$host" -d postgres -c "SELECT 1 FROM pg_database WHERE datname='${to}'")" == "1" ]]; then
    echo "  ${to} already exists on ${host}"
    return
  fi
  if [[ "$(pg "$host" -d postgres -c "SELECT 1 FROM pg_database WHERE datname='${from}'")" != "1" ]]; then
    echo "  WARN: neither ${from} nor ${to} exists on ${host} (fresh DB will be created)"
    return
  fi
  pg "$host" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${from}' AND pid <> pg_backend_pid()" >/dev/null
  pg "$host" -d postgres -c "ALTER DATABASE \"${from}\" RENAME TO \"${to}\""
  echo "  renamed ${from} -> ${to} on ${host}"
}

service_db() {
  local host="$1" db="$2" prefix="$3"
  local lc; lc="$(echo "$prefix" | tr '[:upper:]' '[:lower:]')"
  local app_var="${prefix}_DB_PASSWORD" mig_var="${prefix}_DB_MIGRATION_PASSWORD"
  : "${!app_var:?${app_var} missing in .env}"; : "${!mig_var:?${mig_var} missing in .env}"
  pg "$host" -d postgres -v db="$db" -v migrator="${lc}_migrator" -v migrator_pw="${!mig_var}" \
     -v app="${lc}_app" -v app_pw="${!app_var}" -f - < docker/postgres/service-db.sql >/dev/null
  echo "  ${db}: owner ${lc}_migrator, runtime ${lc}_app"
}

drop_legacy_dbs() {
  local host="$1"; shift
  local keep="'postgres','template0','template1'"
  for k in "$@"; do keep="${keep},'${k}'"; done
  local dbs; dbs="$(pg "$host" -d postgres -c "SELECT datname FROM pg_database WHERE datname NOT IN (${keep})")"
  for d in $dbs; do
    pg "$host" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${d}' AND pid <> pg_backend_pid()" >/dev/null
    pg "$host" -d postgres -c "DROP DATABASE \"${d}\""
    echo "  dropped legacy database ${d} on ${host}"
  done
}

drop_shared_role() {
  local host="$1"; shift
  if [[ "$(pg "$host" -d postgres -c "SELECT 1 FROM pg_roles WHERE rolname='platform'")" != "1" ]]; then
    return
  fi
  for d in "$@"; do pg "$host" -d "$d" -c "REASSIGN OWNED BY platform TO postgres; DROP OWNED BY platform" >/dev/null; done
  pg "$host" -d postgres -c "REASSIGN OWNED BY platform TO postgres; DROP OWNED BY platform; DROP ROLE platform" >/dev/null
  echo "  removed shared superuser role 'platform' on ${host}"
}

echo "==> Ensuring Postgres / MinIO containers are running"
docker compose up -d postgres-hrms postgres-payroll postgres-platform minio >/dev/null

echo "==> 1/4 Rename Qii databases to fixed names"
rename_db postgres-hrms people_qii_creative people_db
rename_db postgres-hrms leave_qii_creative leave_db
rename_db postgres-hrms timework_qii_creative timework_db
rename_db postgres-payroll payroll_qii_creative payroll_db
rename_db postgres-platform files_qii_creative files_db
rename_db postgres-platform notif_qii_creative notification_db
rename_db postgres-platform reports_qii_creative reports_db

echo "==> 2/4 Per-service roles (migrator = schema owner, app = DML only)"
service_db postgres-hrms people_db PEOPLE
service_db postgres-hrms leave_db LEAVE
service_db postgres-hrms timework_db TIMEWORK
service_db postgres-payroll payroll_db PAYROLL
service_db postgres-platform files_db FILES
service_db postgres-platform notification_db NOTIFICATION
service_db postgres-platform reports_db REPORT

echo "==> 3/4 Documents: ${OLD_BUCKET} -> ${NEW_BUCKET}"
NETWORK="${PROJECT}_default"
MC_HOST="http://${MINIO_ROOT_USER}:${MINIO_ROOT_PASSWORD}@minio:9000"
docker run --rm --network "$NETWORK" -e MC_HOST_local="$MC_HOST" minio/mc mb -p "local/${NEW_BUCKET}" >/dev/null
if docker run --rm --network "$NETWORK" -e MC_HOST_local="$MC_HOST" minio/mc ls "local/${OLD_BUCKET}" >/dev/null 2>&1; then
  docker run --rm --network "$NETWORK" -e MC_HOST_local="$MC_HOST" minio/mc mirror --overwrite \
    "local/${OLD_BUCKET}" "local/${NEW_BUCKET}" >/dev/null
  echo "  mirrored objects"
fi
pg postgres-platform -d files_db -c "UPDATE file_object SET storage_key = '${NEW_BUCKET}/' || substring(storage_key from length('${OLD_BUCKET}/') + 1) WHERE storage_key LIKE '${OLD_BUCKET}/%'" >/dev/null
echo "  storage keys now point at ${NEW_BUCKET}"

echo "==> 4/4 Remove legacy multi-tenant databases, registry and shared superuser"
drop_legacy_dbs postgres-hrms people_db leave_db timework_db
drop_legacy_dbs postgres-payroll payroll_db
drop_legacy_dbs postgres-platform files_db notification_db reports_db
drop_shared_role postgres-hrms people_db leave_db timework_db
drop_shared_role postgres-payroll payroll_db
drop_shared_role postgres-platform files_db notification_db reports_db
docker rm -f "${PROJECT}-postgres-registry-1" >/dev/null 2>&1 && echo "  removed registry Postgres container" || true
docker rm -f "${PROJECT}-tenant-registry-1" >/dev/null 2>&1 || true
docker volume rm "${PROJECT}_pg_registry_data" >/dev/null 2>&1 && echo "  removed registry volume" || true

echo "==> Recreate Redis with AUTH (gateway session store)"
docker compose up -d redis >/dev/null

echo ""
echo "Done. Start the services; each one applies its own Liquibase changelog on startup."
echo "Old bucket '${OLD_BUCKET}' is kept as a backup — remove it once documents are verified."
