-- Alert disposal remains available to system administrators and regulators only on the Agent
-- path. Existing page/API behavior keeps its own canteen-scope rules.
DELETE FROM role_permissions
WHERE role_code IN ('CANTEEN_STAFF', 'SCHOOL_ADMIN')
  AND permission_code = 'ALERT_DISPOSE';
