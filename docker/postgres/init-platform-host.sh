#!/bin/bash
set -euo pipefail
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
  SELECT 'CREATE DATABASE platform_host OWNER ${USER_NAME}'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'platform_host')\gexec
  SELECT 'CREATE DATABASE files_platform OWNER ${USER_NAME}'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'files_platform')\gexec
  SELECT 'CREATE DATABASE notif_platform OWNER ${USER_NAME}'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notif_platform')\gexec
EOSQL
