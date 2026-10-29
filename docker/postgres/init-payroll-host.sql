-- Payroll Postgres host. Tenant DBs (payroll_<tenantId>) are created later by Registry.

CREATE USER platform WITH PASSWORD 'platform' SUPERUSER;
CREATE DATABASE payroll_host OWNER platform;
