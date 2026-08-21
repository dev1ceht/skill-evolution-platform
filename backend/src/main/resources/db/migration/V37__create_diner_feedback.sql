CREATE TABLE meal_reviews (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    review_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    rating INTEGER NOT NULL,
    content VARCHAR(2000),
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, review_id),
    CONSTRAINT uk_meal_reviews_order UNIQUE (school_id, canteen_id, actor_user_id, order_id),
    CONSTRAINT uk_meal_reviews_idempotency UNIQUE (
        school_id, canteen_id, actor_user_id, idempotency_key),
    CONSTRAINT fk_meal_review_actor FOREIGN KEY (actor_user_id) REFERENCES app_users(user_id),
    CONSTRAINT fk_meal_review_order FOREIGN KEY (school_id, canteen_id, order_id)
        REFERENCES meal_orders(school_id, canteen_id, order_id),
    CONSTRAINT ck_meal_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_meal_review_status CHECK (status IN ('SUBMITTED')),
    CONSTRAINT ck_meal_review_version CHECK (version >= 0)
);

CREATE INDEX idx_meal_reviews_actor_time
    ON meal_reviews(school_id, canteen_id, actor_user_id, created_at, review_id);

CREATE TABLE diner_complaints (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    complaint_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    subject VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    related_order_id VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    reply VARCHAR(3000),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, complaint_id),
    CONSTRAINT uk_diner_complaints_idempotency UNIQUE (
        school_id, canteen_id, actor_user_id, idempotency_key),
    CONSTRAINT fk_diner_complaint_actor FOREIGN KEY (actor_user_id) REFERENCES app_users(user_id),
    CONSTRAINT fk_diner_complaint_order FOREIGN KEY (school_id, canteen_id, related_order_id)
        REFERENCES meal_orders(school_id, canteen_id, order_id),
    CONSTRAINT ck_diner_complaint_category CHECK (
        category IN ('FOOD_QUALITY', 'SERVICE', 'HYGIENE', 'QUEUE', 'PAYMENT', 'OTHER')),
    CONSTRAINT ck_diner_complaint_status CHECK (status IN ('SUBMITTED')),
    CONSTRAINT ck_diner_complaint_version CHECK (version >= 0)
);

CREATE INDEX idx_diner_complaints_actor_time
    ON diner_complaints(school_id, canteen_id, actor_user_id, created_at, complaint_id);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'MEAL_REVIEW_READ', '查看个人就餐评价', 'meal_review', 'read', '查看当前用户自己的就餐评价'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'MEAL_REVIEW_READ'
);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'MEAL_REVIEW_WRITE', '提交个人就餐评价', 'meal_review', 'write', '提交当前用户自己的就餐评价'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'MEAL_REVIEW_WRITE'
);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'DINER_COMPLAINT_READ', '查看个人投诉', 'diner_complaint', 'read', '查看当前用户自己的食堂投诉'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'DINER_COMPLAINT_READ'
);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'DINER_COMPLAINT_WRITE', '提交个人投诉', 'diner_complaint', 'write', '提交当前用户自己的食堂投诉'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'DINER_COMPLAINT_WRITE'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'MEAL_REVIEW_READ'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'MEAL_REVIEW_READ'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'MEAL_REVIEW_WRITE'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'MEAL_REVIEW_WRITE'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'DINER_COMPLAINT_READ'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'DINER_COMPLAINT_READ'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'DINER_COMPLAINT_WRITE'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'DINER_COMPLAINT_WRITE'
);
