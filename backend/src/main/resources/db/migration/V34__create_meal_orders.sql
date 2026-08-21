CREATE TABLE meal_orders (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    meal_date DATE NOT NULL,
    meal_time VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    payment_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    total_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, order_id),
    CONSTRAINT uk_meal_orders_scope_no UNIQUE (school_id, canteen_id, order_no),
    CONSTRAINT uk_meal_orders_scope_idempotency UNIQUE (school_id, canteen_id, idempotency_key),
    CONSTRAINT fk_meal_order_actor FOREIGN KEY (actor_user_id) REFERENCES app_users(user_id),
    CONSTRAINT fk_meal_order_menu FOREIGN KEY (school_id, canteen_id, menu_id)
        REFERENCES daily_menus(school_id, canteen_id, menu_id),
    CONSTRAINT ck_meal_order_time CHECK (meal_time IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    CONSTRAINT ck_meal_order_status CHECK (status IN ('CREATED', 'CANCELLED')),
    CONSTRAINT ck_meal_order_payment CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    CONSTRAINT ck_meal_order_amount CHECK (total_amount >= 0)
);

CREATE INDEX idx_meal_orders_actor_time
    ON meal_orders(school_id, canteen_id, actor_user_id, created_at, order_id);

CREATE TABLE meal_order_items (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    dish_id VARCHAR(64) NOT NULL,
    dish_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL DEFAULT 0,
    amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (school_id, canteen_id, order_id, dish_id),
    CONSTRAINT fk_meal_order_item_order FOREIGN KEY (school_id, canteen_id, order_id)
        REFERENCES meal_orders(school_id, canteen_id, order_id),
    CONSTRAINT ck_meal_order_item_quantity CHECK (quantity > 0 AND quantity <= 20),
    CONSTRAINT ck_meal_order_item_price CHECK (unit_price >= 0 AND amount >= 0)
);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'MEAL_ORDER_READ', '查看个人消费订单', 'meal_order', 'read', '查看当前用户自己的消费订单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'MEAL_ORDER_READ'
);

INSERT INTO permissions (permission_code, name, resource, action, description)
SELECT 'MEAL_ORDER_WRITE', '创建和取消消费订单', 'meal_order', 'write', '创建或取消当前用户自己的消费订单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'MEAL_ORDER_WRITE'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'MEAL_ORDER_READ'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'MEAL_ORDER_READ'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'DINER', 'MEAL_ORDER_WRITE'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_code = 'DINER' AND permission_code = 'MEAL_ORDER_WRITE'
);
