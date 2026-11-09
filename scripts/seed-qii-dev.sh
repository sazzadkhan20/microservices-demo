#!/usr/bin/env bash
# Qii Creative development/staging seed (data-only snapshot of the reference Qii dataset).
#
#   bash scripts/seed-qii-dev.sh export   # refresh scripts/seed/qii/*.sql from the current local databases
#   bash scripts/seed-qii-dev.sh load     # load the snapshot into EMPTY databases of a fresh environment
#
# Order for a fresh environment: docker compose up -d → start every service once (each applies its own
# Liquibase schema) → seed load. Production does NOT use this: it starts from the schema plus a first
# super-admin bootstrap and imports real HR data (guide §2 — business data is not silently invented).
# SEED_SINGLE_CONTAINER=<name> loads every database into one Postgres container (restore drills / CI).
# Document binaries are not part of the snapshot; seeded file rows reference objects that only exist
# in the environment the snapshot was exported from.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROJECT="${COMPOSE_PROJECT_NAME:-microservices-demo}"
DIR="$ROOT/scripts/seed/qii"
# host:database pairs (fixed layout from docker-compose.yml)
TARGETS="postgres-hrms:people_db postgres-hrms:leave_db postgres-hrms:timework_db postgres-payroll:payroll_db \
postgres-platform:files_db postgres-platform:notification_db postgres-platform:reports_db"
# Liquibase bookkeeping and transient messaging state are never seeded.
EXCLUDE=(--exclude-table=databasechangelog --exclude-table=databasechangeloglock
         --exclude-table=message_outbox --exclude-table=message_inbox)

mode="${1:-}"
case "$mode" in
  export)
    mkdir -p "$DIR"
    for t in $TARGETS; do
      host="${t%%:*}"; db="${t##*:}"
      docker exec "${PROJECT}-${host}-1" pg_dump -U postgres --data-only --no-owner --no-privileges \
        --disable-triggers "${EXCLUDE[@]}" "$db" > "$DIR/${db}.sql"
      echo "exported $db ($(wc -c < "$DIR/${db}.sql") bytes)"
    done
    ;;
  load)
    # Safety: never overwrite an environment that already holds company data.
    emp="$(docker exec "${SEED_SINGLE_CONTAINER:-${PROJECT}-postgres-hrms-1}" psql -U postgres -d people_db -tAc "SELECT count(*) FROM employee" 2>/dev/null || echo error)"
    if [[ "$emp" != "0" && "${FORCE:-0}" != "1" ]]; then
      echo "people_db.employee has ${emp} rows — refusing to seed (set FORCE=1 to replace ALL data in a dev environment)"
      exit 1
    fi
    for t in $TARGETS; do
      host="${t%%:*}"; db="${t##*:}"
      [[ -f "$DIR/${db}.sql" ]] || { echo "missing $DIR/${db}.sql"; exit 1; }
      # Replace migration-inserted reference rows with the snapshot, atomically per database.
      { echo "BEGIN;"
        cat <<'SQL'
DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public'
             AND tablename NOT IN ('databasechangelog', 'databasechangeloglock') LOOP
    EXECUTE format('TRUNCATE TABLE public.%I RESTART IDENTITY CASCADE', r.tablename);
  END LOOP;
END $$;
SQL
        grep -v -E "^(BEGIN|COMMIT);?$" "$DIR/${db}.sql"
        echo "COMMIT;"
      } | docker exec -i "${SEED_SINGLE_CONTAINER:-${PROJECT}-${host}-1}" psql -v ON_ERROR_STOP=1 -q -U postgres -d "$db" >/dev/null
      echo "seeded $db"
    done
    ;;
  *)
    echo "usage: $0 export|load"; exit 2 ;;
esac
