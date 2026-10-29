-- Manual org + leave upgrade for existing hrms_techshop_retail
-- Prefer: Registry POST /api/registry/tenants/techshop_retail/upgrade-hrms?reseedPracticeLeave=true
-- after rebuilding tenant-registry (Liquibase 003 + PracticeBusinessSeeder).

-- If Liquibase already applied 003, skip DDL and only reseed via Registry API.
