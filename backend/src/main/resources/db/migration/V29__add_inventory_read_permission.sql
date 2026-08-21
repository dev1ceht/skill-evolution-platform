-- SC-005: expose scoped inventory read access to the operations and management personas.

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES ('INVENTORY_READ', '查看库存', 'inventory', 'read', '查询当前食堂库存和系统低库存标记');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'INVENTORY_READ'),
    ('SCHOOL_ADMIN', 'INVENTORY_READ'),
    ('CANTEEN_STAFF', 'INVENTORY_READ');
