-- Phase 4: expose menu duties as configurable permission codes.

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES
    ('MENU_VALIDATE', '校验菜单', 'daily_menu', 'validate', '校验菜单版本、菜品、配方和餐次唯一性'),
    ('MENU_SUBMIT', '提交菜单审批', 'daily_menu', 'submit', '提交日菜单进入领域审批'),
    ('MENU_APPROVE', '审批菜单', 'daily_menu', 'approve', '批准或拒绝待审批日菜单'),
    ('MENU_PUBLISH', '发布菜单', 'daily_menu', 'publish', '发布已批准日菜单');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'MENU_VALIDATE'),
    ('SYSTEM_ADMIN', 'MENU_SUBMIT'),
    ('SYSTEM_ADMIN', 'MENU_APPROVE'),
    ('SYSTEM_ADMIN', 'MENU_PUBLISH'),
    ('SCHOOL_ADMIN', 'MENU_VALIDATE'),
    ('SCHOOL_ADMIN', 'MENU_SUBMIT'),
    ('SCHOOL_ADMIN', 'MENU_APPROVE'),
    ('SCHOOL_ADMIN', 'MENU_PUBLISH'),
    ('CANTEEN_STAFF', 'MENU_VALIDATE'),
    ('CANTEEN_STAFF', 'MENU_SUBMIT');
