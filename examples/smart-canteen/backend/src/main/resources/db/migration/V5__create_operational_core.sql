-- Phase 5 is additive. V1-V4 remain immutable so the existing demo workflow
-- can be upgraded before the operational tables are adopted by the UI.

ALTER TABLE inventory
    ADD warning_threshold DECIMAL(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE inventory
    ADD last_update_time TIMESTAMP NULL;

UPDATE inventory
SET last_update_time = CURRENT_TIMESTAMP
WHERE last_update_time IS NULL;

CREATE TABLE app_users (
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    school_id VARCHAR(64),
    canteen_id VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_app_users_username UNIQUE (username),
    CONSTRAINT ck_app_users_role CHECK (role IN (
        'SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'CANTEEN_STAFF', 'REGULATOR', 'SUPPLIER'
    )),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_app_users_scope ON app_users(school_id, canteen_id, status);

CREATE TABLE auth_refresh_sessions (
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    refresh_token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    CONSTRAINT uk_auth_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_refresh_user FOREIGN KEY (user_id) REFERENCES app_users(user_id)
);

CREATE INDEX idx_auth_refresh_lookup
    ON auth_refresh_sessions(refresh_token_hash, revoked_at, expires_at);

CREATE TABLE ingredients (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(64) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    specification VARCHAR(100),
    energy_kcal DECIMAL(12, 4) NOT NULL DEFAULT 0,
    protein_g DECIMAL(12, 4) NOT NULL DEFAULT 0,
    fat_g DECIMAL(12, 4) NOT NULL DEFAULT 0,
    carbohydrate_g DECIMAL(12, 4) NOT NULL DEFAULT 0,
    warning_threshold DECIMAL(19, 4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, ingredient_id),
    CONSTRAINT uk_ingredients_scope_name UNIQUE (school_id, canteen_id, name),
    CONSTRAINT ck_ingredients_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_ingredients_scope_category
    ON ingredients(school_id, canteen_id, category, name);

CREATE TABLE dishes (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    dish_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description VARCHAR(1000),
    image_url VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, dish_id),
    CONSTRAINT uk_dishes_scope_name UNIQUE (school_id, canteen_id, name),
    CONSTRAINT ck_dishes_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dish_ingredients (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    dish_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, dish_id, ingredient_id),
    CONSTRAINT ck_dish_ingredients_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_dish_ingredients_ingredient
    ON dish_ingredients(school_id, canteen_id, ingredient_id);

CREATE TABLE daily_menus (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    menu_date DATE NOT NULL,
    meal_time VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, menu_id),
    CONSTRAINT uk_daily_menu_scope_slot
        UNIQUE (school_id, canteen_id, menu_date, meal_time),
    CONSTRAINT ck_daily_menu_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_daily_menus_scope_date
    ON daily_menus(school_id, canteen_id, menu_date, meal_time);

CREATE TABLE daily_menu_items (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    dish_id VARCHAR(64) NOT NULL,
    estimated_quantity DECIMAL(19, 4) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (school_id, canteen_id, menu_id, dish_id),
    CONSTRAINT ck_daily_menu_items_quantity CHECK (estimated_quantity > 0)
);

CREATE TABLE suppliers (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    supplier_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(100),
    contact_phone VARCHAR(32),
    license_no VARCHAR(100),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, supplier_id),
    CONSTRAINT uk_suppliers_scope_name UNIQUE (school_id, canteen_id, name),
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE purchase_orders (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    supplier_id VARCHAR(64) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    expected_delivery_at TIMESTAMP NULL,
    total_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(1000),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, order_id),
    CONSTRAINT uk_purchase_orders_scope_no UNIQUE (school_id, canteen_id, order_no),
    CONSTRAINT uk_purchase_orders_scope_idempotency
        UNIQUE (school_id, canteen_id, idempotency_key),
    CONSTRAINT ck_purchase_orders_type CHECK (order_type IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT ck_purchase_orders_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'CONFIRMED', 'RECEIVED', 'CANCELLED')
    )
);

CREATE INDEX idx_purchase_orders_scope_status_time
    ON purchase_orders(school_id, canteen_id, status, created_at);

CREATE TABLE purchase_order_items (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, order_id, ingredient_id),
    CONSTRAINT ck_purchase_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_price CHECK (unit_price >= 0)
);

CREATE TABLE purchase_receipts (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    receipt_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, receipt_id),
    CONSTRAINT uk_purchase_receipts_scope_idempotency
        UNIQUE (school_id, canteen_id, idempotency_key)
);

CREATE TABLE inventory_batches (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    supplier_id VARCHAR(64) NOT NULL,
    batch_no VARCHAR(100) NOT NULL,
    quantity_base DECIMAL(19, 4) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    purchase_price DECIMAL(19, 4) NOT NULL,
    production_date DATE NULL,
    expiry_date DATE NULL,
    trace_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, batch_id),
    CONSTRAINT uk_inventory_batches_trace UNIQUE (school_id, canteen_id, trace_code),
    CONSTRAINT ck_inventory_batches_quantity CHECK (quantity_base > 0)
);

CREATE INDEX idx_inventory_batches_ingredient_expiry
    ON inventory_batches(school_id, canteen_id, ingredient_id, expiry_date);

CREATE TABLE purchase_receipt_items (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    receipt_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    quantity_base DECIMAL(19, 4) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, receipt_id, batch_id),
    CONSTRAINT ck_purchase_receipt_items_quantity CHECK (quantity_base > 0)
);

CREATE TABLE stock_out_records (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    stock_out_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, stock_out_id),
    CONSTRAINT uk_stock_out_scope_idempotency
        UNIQUE (school_id, canteen_id, idempotency_key)
);

CREATE TABLE stock_out_items (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    stock_out_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    quantity_base DECIMAL(19, 4) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, stock_out_id, ingredient_id),
    CONSTRAINT ck_stock_out_items_quantity CHECK (quantity_base > 0)
);

CREATE TABLE operational_ledger_records (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    record_id VARCHAR(64) NOT NULL,
    cycle_id VARCHAR(64) NOT NULL,
    ledger_code VARCHAR(64) NOT NULL,
    record_time TIMESTAMP NOT NULL,
    recorder_id VARCHAR(64),
    content_json TEXT NOT NULL,
    photos_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, record_id),
    CONSTRAINT uk_operational_ledger_cycle_code
        UNIQUE (school_id, canteen_id, cycle_id, ledger_code),
    CONSTRAINT ck_operational_ledger_status CHECK (status IN ('COMPLETED', 'VOID'))
);

CREATE INDEX idx_operational_ledger_scope_time
    ON operational_ledger_records(school_id, canteen_id, record_time, status);

CREATE TABLE traceability_records (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    trace_code VARCHAR(100) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    supplier_id VARCHAR(64) NOT NULL,
    quantity_base DECIMAL(19, 4) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, trace_code),
    CONSTRAINT ck_traceability_quantity CHECK (quantity_base > 0)
);

CREATE INDEX idx_traceability_batch
    ON traceability_records(school_id, canteen_id, batch_id);
