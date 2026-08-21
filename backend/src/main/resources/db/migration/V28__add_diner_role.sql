-- SC-003: employee/student users are assistant consumers, not operations staff.

ALTER TABLE app_users
    DROP CONSTRAINT ck_app_users_role;

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_role CHECK (role IN (
        'SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'CANTEEN_STAFF', 'REGULATOR', 'SUPPLIER', 'DINER'
    ));

INSERT INTO roles (role_code, name, description, system_role)
SELECT 'DINER', '员工/学生', '查询菜单、菜品和个人就餐信息', FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE role_code = 'DINER'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'MENU_READ'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'MENU_READ'
);
