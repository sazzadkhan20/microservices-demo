-- HRMS Postgres host. Tenant DBs (hrms_<tenantId>) are created later by Registry.

CREATE USER platform WITH PASSWORD 'platform' SUPERUSER;
CREATE DATABASE hrms_host OWNER platform;
