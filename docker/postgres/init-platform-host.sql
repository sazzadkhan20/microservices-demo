-- Shared platform Postgres host for files_* and notif_* tenant databases.

CREATE USER platform WITH PASSWORD 'platform' SUPERUSER;
CREATE DATABASE platform_host OWNER platform;
CREATE DATABASE files_platform OWNER platform;
CREATE DATABASE notif_platform OWNER platform;
