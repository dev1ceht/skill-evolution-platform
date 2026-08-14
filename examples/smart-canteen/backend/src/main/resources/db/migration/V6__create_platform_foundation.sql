-- Phase 1 platform foundation. Existing V1-V5 tables remain compatible.

ALTER TABLE app_users
    ADD scope_management_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing accounts are migrated into the platform scope model below. The flag is
-- set only after valid legacy scopes have been backfilled at the end of this file.
-- New legacy rows that do not set this flag may still use their primary app_users
-- scope until they are explicitly managed through the platform API.

ALTER TABLE schools
    ADD region_code VARCHAR(64) NOT NULL DEFAULT 'DEFAULT-REGION';

ALTER TABLE schools
    ADD status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE schools
    ADD updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE schools
SET region_code = CONCAT('LEGACY-', SUBSTRING(id, 1, 57))
WHERE region_code = 'DEFAULT-REGION';

ALTER TABLE canteens
    ADD address VARCHAR(255);

ALTER TABLE canteens
    ADD status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE canteens
    ADD updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_schools_region_status
    ON schools(region_code, status, id);

CREATE INDEX idx_canteens_school_status
    ON canteens(school_id, status, id);

ALTER TABLE canteens
    ADD CONSTRAINT uk_canteen_school_pair UNIQUE (school_id, id);

CREATE TABLE roles (
    role_code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_roles_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE permissions (
    permission_code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
    role_code VARCHAR(32) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_code) REFERENCES roles(role_code),
    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_code) REFERENCES permissions(permission_code)
);

CREATE TABLE user_roles (
    user_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_users(user_id),
    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_code) REFERENCES roles(role_code)
);

CREATE INDEX idx_user_roles_role ON user_roles(role_code, user_id);

CREATE TABLE user_scope_assignments (
    assignment_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    region_code VARCHAR(64),
    school_id VARCHAR(64),
    canteen_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scope_assignment_user
        FOREIGN KEY (user_id) REFERENCES app_users(user_id),
    CONSTRAINT fk_scope_assignment_school
        FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_scope_assignment_canteen
        FOREIGN KEY (canteen_id) REFERENCES canteens(id),
    CONSTRAINT fk_scope_assignment_school_canteen
        FOREIGN KEY (school_id, canteen_id) REFERENCES canteens(school_id, id),
    CONSTRAINT ck_scope_assignment_type
        CHECK (scope_type IN ('REGION', 'SCHOOL', 'CANTEEN')),
    CONSTRAINT ck_scope_assignment_shape
        CHECK (
            (scope_type = 'REGION' AND region_code IS NOT NULL AND school_id IS NULL AND canteen_id IS NULL)
            OR (scope_type = 'SCHOOL' AND region_code IS NULL AND school_id IS NOT NULL AND canteen_id IS NULL)
            OR (scope_type = 'CANTEEN' AND region_code IS NULL AND school_id IS NOT NULL AND canteen_id IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uk_user_scope_assignment
    ON user_scope_assignments(user_id, scope_type, region_code, school_id, canteen_id);

CREATE INDEX idx_user_scope_assignment_user
    ON user_scope_assignments(user_id, scope_type);

CREATE TABLE audit_logs (
    audit_id VARCHAR(64) PRIMARY KEY,
    actor_user_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    school_id VARCHAR(64),
    canteen_id VARCHAR(64),
    outcome VARCHAR(16) NOT NULL,
    detail VARCHAR(2000),
    request_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_users(user_id),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_logs_time
    ON audit_logs(created_at, audit_id);

CREATE INDEX idx_audit_logs_scope
    ON audit_logs(school_id, canteen_id, created_at);

INSERT INTO roles (role_code, name, description, system_role)
VALUES
    ('SYSTEM_ADMIN', '系统管理员', '管理平台组织、账号、权限和全局配置', TRUE),
    ('SCHOOL_ADMIN', '学校管理员', '管理本学校食堂和运营账号', FALSE),
    ('CANTEEN_STAFF', '食堂工作人员', '执行食堂日常运营工作', FALSE),
    ('REGULATOR', '监管人员', '按授权区域查看监管数据', FALSE),
    ('SUPPLIER', '供应商', '查看和处理被授权的供应商业务', FALSE);

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES
    ('ORG_READ', '查看组织', 'organization', 'read', '查看学校和食堂主数据'),
    ('ORG_WRITE', '维护组织', 'organization', 'write', '创建、修改和停用学校/食堂'),
    ('USER_READ', '查看账号', 'user', 'read', '查看子账号和账号状态'),
    ('USER_WRITE', '维护账号', 'user', 'write', '创建、停用和分配账号'),
    ('ROLE_READ', '查看角色权限', 'role', 'read', '查看角色和权限目录'),
    ('ROLE_WRITE', '维护角色权限', 'role', 'write', '维护角色权限关联'),
    ('AUDIT_READ', '查看审计', 'audit', 'read', '查询关键管理操作审计'),
    ('AUDIT_WRITE', '写入审计', 'audit', 'write', '记录关键管理操作审计');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'ORG_READ'),
    ('SYSTEM_ADMIN', 'ORG_WRITE'),
    ('SYSTEM_ADMIN', 'USER_READ'),
    ('SYSTEM_ADMIN', 'USER_WRITE'),
    ('SYSTEM_ADMIN', 'ROLE_READ'),
    ('SYSTEM_ADMIN', 'ROLE_WRITE'),
    ('SYSTEM_ADMIN', 'AUDIT_READ'),
    ('SYSTEM_ADMIN', 'AUDIT_WRITE'),
    ('SCHOOL_ADMIN', 'ORG_READ'),
    ('SCHOOL_ADMIN', 'ORG_WRITE'),
    ('SCHOOL_ADMIN', 'USER_READ'),
    ('SCHOOL_ADMIN', 'USER_WRITE'),
    ('SCHOOL_ADMIN', 'ROLE_READ'),
    ('SCHOOL_ADMIN', 'AUDIT_READ'),
    ('CANTEEN_STAFF', 'ORG_READ'),
    ('REGULATOR', 'ORG_READ'),
    ('REGULATOR', 'ROLE_READ'),
    ('REGULATOR', 'AUDIT_READ'),
    ('SUPPLIER', 'ORG_READ');

INSERT INTO user_roles (user_id, role_code)
SELECT user_id, role
FROM app_users;

INSERT INTO user_scope_assignments (
    assignment_id, user_id, scope_type, school_id, canteen_id
)
SELECT
    CONCAT('SCOPE-', user_id), user_id, 'CANTEEN', school_id, canteen_id
FROM app_users
WHERE school_id IS NOT NULL
  AND canteen_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM canteens c
      WHERE c.id = app_users.canteen_id
        AND c.school_id = app_users.school_id
  );

INSERT INTO user_scope_assignments (
    assignment_id, user_id, scope_type, school_id
)
SELECT
    CONCAT('SCHOOL-SCOPE-', user_id), user_id, 'SCHOOL', school_id
FROM app_users
WHERE school_id IS NOT NULL
  AND canteen_id IS NULL
  AND EXISTS (SELECT 1 FROM schools s WHERE s.id = app_users.school_id);

UPDATE app_users
SET scope_management_enabled = TRUE
WHERE (school_id IS NULL AND canteen_id IS NULL)
   OR (school_id IS NOT NULL AND canteen_id IS NULL
       AND EXISTS (SELECT 1 FROM schools s WHERE s.id = app_users.school_id))
   OR (school_id IS NOT NULL AND canteen_id IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM canteens c
           WHERE c.id = app_users.canteen_id
             AND c.school_id = app_users.school_id
       ));
