-- Guide §1/§5: one fixed database per service, owned by that service.
--   <svc>_migrator : schema owner, used ONLY by Liquibase (spring.liquibase.user)
--   <svc>_app      : runtime role (spring.datasource.username) — DML only, no DDL, not superuser
-- Idempotent: used by first-boot container init AND by scripts/migrate-local-to-fixed-db.sh.
--
-- psql variables: db, migrator, migrator_pw, app, app_pw
\set ON_ERROR_STOP on

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'migrator', :'migrator_pw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'migrator') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
              :'migrator', :'migrator_pw') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app', :'app_pw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
              :'app', :'app_pw') \gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'migrator')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'db', :'migrator') \gexec
-- No other role (including other services' roles) may connect to this database.
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'db', :'app') \gexec

\connect :db

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'migrator') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app') \gexec

-- Existing objects (e.g. created earlier by a shared superuser) are handed to the migration role.
SELECT format('ALTER TABLE public.%I OWNER TO %I', tablename, :'migrator')
FROM pg_tables WHERE schemaname = 'public' AND tableowner <> :'migrator' \gexec
SELECT format('ALTER VIEW public.%I OWNER TO %I', viewname, :'migrator')
FROM pg_views WHERE schemaname = 'public' AND viewowner <> :'migrator' \gexec
SELECT format('ALTER SEQUENCE public.%I OWNER TO %I', c.relname, :'migrator')
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'S' AND pg_get_userbyid(c.relowner) <> :'migrator'
  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i')) \gexec

-- Runtime role: data access only.
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app') \gexec
SELECT format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I', :'app') \gexec
-- Tables/sequences Liquibase creates later get the same grants automatically.
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
              :'migrator', :'app') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I',
              :'migrator', :'app') \gexec
