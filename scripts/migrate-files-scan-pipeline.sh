#!/usr/bin/env bash
# Apply guide §8 scan-pipeline columns to every files_* tenant DB (idempotent).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a
# shellcheck disable=SC1091
. "$ROOT/.env"
set +a

SQL=$(cat <<'EOS'
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS scan_status VARCHAR(32) NOT NULL DEFAULT 'QUARANTINED';
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS classification VARCHAR(64) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS employee_id BIGINT;
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS scan_detail TEXT;
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS scanned_at TIMESTAMPTZ;
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS released_at TIMESTAMPTZ;
EOS
)

for db in $(docker exec -e PGPASSWORD="$FILES_PG_PASSWORD" microservices-demo-postgres-platform-1 \
  psql -U "$FILES_PG_USER" -d postgres -tAc "SELECT datname FROM pg_database WHERE datname LIKE 'files_%' ORDER BY 1;"); do
  echo "Migrating $db"
  docker exec -e PGPASSWORD="$FILES_PG_PASSWORD" microservices-demo-postgres-platform-1 \
    psql -U "$FILES_PG_USER" -d "$db" -v ON_ERROR_STOP=1 -c "$SQL" >/dev/null
done
echo "Done."
