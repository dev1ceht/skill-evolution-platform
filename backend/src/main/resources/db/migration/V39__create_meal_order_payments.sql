CREATE TABLE meal_order_payments (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    payment_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    method VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCEEDED',
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, payment_id),
    CONSTRAINT uk_meal_order_payments_order UNIQUE (school_id, canteen_id, order_id),
    CONSTRAINT uk_meal_order_payments_idempotency UNIQUE (
        school_id, canteen_id, actor_user_id, idempotency_key),
    CONSTRAINT fk_meal_order_payment_actor FOREIGN KEY (actor_user_id)
        REFERENCES app_users(user_id),
    CONSTRAINT fk_meal_order_payment_order FOREIGN KEY (school_id, canteen_id, order_id)
        REFERENCES meal_orders(school_id, canteen_id, order_id),
    CONSTRAINT ck_meal_order_payment_amount CHECK (amount >= 0),
    CONSTRAINT ck_meal_order_payment_method CHECK (method IN ('STUDY_MOCK')),
    CONSTRAINT ck_meal_order_payment_status CHECK (status IN ('SUCCEEDED')),
    CONSTRAINT ck_meal_order_payment_version CHECK (version >= 0)
);

CREATE INDEX idx_meal_order_payments_actor_time
    ON meal_order_payments(school_id, canteen_id, actor_user_id, created_at, payment_id);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'MEAL_PAYMENT_WRITE', '执行个人订单模拟支付', 'meal_payment', 'write',
       '执行当前用户自己的学习环境模拟支付'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'MEAL_PAYMENT_WRITE'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'MEAL_PAYMENT_WRITE'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'MEAL_PAYMENT_WRITE'
);
