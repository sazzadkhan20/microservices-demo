#!/bin/bash
# First-boot init for a PostgreSQL host (docker-entrypoint-initdb.d).
# SERVICE_DBS lists "<db>:<ENV_PREFIX>" pairs; passwords come from <ENV_PREFIX>_DB_PASSWORD and
# <ENV_PREFIX>_DB_MIGRATION_PASSWORD (never baked into the image).
set -euo pipefail
: "${SERVICE_DBS:?SERVICE_DBS is required, e.g. people_db:PEOPLE leave_db:LEAVE}"
for pair in $SERVICE_DBS; do
  db="${pair%%:*}"; prefix="${pair##*:}"; lc="$(echo "$prefix" | tr '[:upper:]' '[:lower:]')"
  app_pw_var="${prefix}_DB_PASSWORD"; mig_pw_var="${prefix}_DB_MIGRATION_PASSWORD"
  app_pw="${!app_pw_var:?${app_pw_var} is required}"; mig_pw="${!mig_pw_var:?${mig_pw_var} is required}"
  echo "Provisioning database ${db} (roles ${lc}_migrator / ${lc}_app)"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v db="$db" -v migrator="${lc}_migrator" -v migrator_pw="$mig_pw" -v app="${lc}_app" -v app_pw="$app_pw" \
    -f /docker-entrypoint-initdb.d/service-db.sql.tpl
done
