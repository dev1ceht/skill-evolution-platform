-- Phase 2: allow the assistant to read menu aggregates through the same Skill gate.

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES
    ('MENU_READ', '查看菜单', 'daily_menu', 'read', '查看当前范围内的日菜单和菜品清单');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'MENU_READ'),
    ('SCHOOL_ADMIN', 'MENU_READ'),
    ('CANTEEN_STAFF', 'MENU_READ'),
    ('REGULATOR', 'MENU_READ');
