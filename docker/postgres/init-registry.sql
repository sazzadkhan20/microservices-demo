-- Control-plane Postgres. Visible immediately after `docker compose up`.
-- `platform` is SUPERUSER for local/practice provisioning convenience.

CREATE USER platform WITH PASSWORD 'platform' SUPERUSER;
CREATE DATABASE registry_db OWNER platform;
