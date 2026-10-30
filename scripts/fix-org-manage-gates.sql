-- Revoke org structure mutate from HR/Manager (prototype: hierarchy.edit is Admin+ only).
DELETE FROM app_role_permission rp
USING app_role r, app_permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND UPPER(r.code) IN ('HR','MANAGER')
  AND (
    (p.feature = 'org' AND p.action = 'manage')
    OR (p.feature = 'hierarchy' AND p.action = 'edit')
  );

-- Ensure HR keeps holidays mutate + hierarchy view (prototype).
INSERT INTO app_role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'ALL'
FROM app_role r CROSS JOIN app_permission p
WHERE UPPER(r.code) = 'HR'
  AND (
    (p.feature = 'hierarchy' AND p.action = 'view')
    OR (p.feature = 'holidays' AND p.action IN ('view','create','edit','delete'))
    OR (p.feature = 'attendance_settings' AND p.action IN ('view','edit'))
    OR (p.feature = 'org' AND p.action = 'view')
  )
  AND NOT EXISTS (
    SELECT 1 FROM app_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- Manager: hierarchy view + holidays view only (no org manage).
INSERT INTO app_role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'ALL'
FROM app_role r CROSS JOIN app_permission p
WHERE UPPER(r.code) = 'MANAGER'
  AND (
    (p.feature = 'hierarchy' AND p.action = 'view')
    OR (p.feature = 'holidays' AND p.action = 'view')
    OR (p.feature = 'org' AND p.action = 'view')
  )
  AND NOT EXISTS (
    SELECT 1 FROM app_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- Admin/SuperAdmin keep hierarchy.edit (org structure).
INSERT INTO app_role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'ALL'
FROM app_role r CROSS JOIN app_permission p
WHERE UPPER(r.code) IN ('ADMIN','SUPERADMIN')
  AND p.feature = 'hierarchy' AND p.action = 'edit'
  AND NOT EXISTS (
    SELECT 1 FROM app_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id
  );

SELECT r.code, p.feature || ':' || p.action AS perm
FROM app_role r
JOIN app_role_permission rp ON rp.role_id = r.id
JOIN app_permission p ON p.id = rp.permission_id
WHERE UPPER(r.code) IN ('HR','MANAGER','ADMIN')
  AND (p.feature IN ('org','hierarchy') OR (p.feature = 'holidays' AND p.action IN ('create','edit')))
ORDER BY r.code, perm;
