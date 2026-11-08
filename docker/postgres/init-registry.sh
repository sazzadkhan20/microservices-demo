#!/bin/bash
set -euo pipefail
# Runs once on empty data volume. Credentials come from container env.
USER_NAME="${PLATFORM_DB_USER:-platform}"
USER_PASS="${PLATFORM_DB_PASSWORD:?PLATFORM_DB_PASSWORD is required}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${USER_NAME}') THEN
      CREATE USER ${USER_NAME} WITH PASSWORD '${USER_PASS}' SUPERUSER;
    END IF;
  END
  \$\$;
  SELECT 'CREATE DATABASE registry_db OWNER ${USER_NAME}'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'registry_db')\gexec
EOSQL
