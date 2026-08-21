-- SC-006: expose read-only recipe and supply gap analysis to operations personas.

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES (
    'PROCUREMENT_ANALYSIS_READ',
    '查看采购缺口分析',
    'procurement',
    'read',
    '按已发布菜单、菜品配方、库存和在途采购快照计算原料缺口');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'PROCUREMENT_ANALYSIS_READ'),
    ('SCHOOL_ADMIN', 'PROCUREMENT_ANALYSIS_READ'),
    ('CANTEEN_STAFF', 'PROCUREMENT_ANALYSIS_READ');
