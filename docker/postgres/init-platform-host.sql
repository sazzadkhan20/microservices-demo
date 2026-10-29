-- Platform Postgres host (port 5436): database-per-tenant for satellite services.
-- Naming (config-driven, reusable for any business):
--   files_<tenantId>  → file-service
--   notif_<tenantId>  → notification-service
--   reports_<tenantId> → report-service
-- Legacy shared DBs below are optional sources for upgrade-satellites migration only.

CREATE USER platform WITH PASSWORD 'platform' SUPERUSER;
CREATE DATABASE platform_host OWNER platform;
CREATE DATABASE files_platform OWNER platform;
CREATE DATABASE notif_platform OWNER platform;
