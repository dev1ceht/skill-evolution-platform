-- Phase 2: persist the recipe-driven procurement loop without rewriting V1-V6.

ALTER TABLE purchase_order_items
    ADD received_quantity_base DECIMAL(19, 4) NOT NULL DEFAULT 0;

CREATE INDEX idx_purchase_order_items_receiving
    ON purchase_order_items(school_id, canteen_id, ingredient_id, received_quantity_base);

CREATE TABLE ingredient_units (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    unit_code VARCHAR(16) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    to_base_factor DECIMAL(19, 8) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, ingredient_id, unit_code),
    CONSTRAINT ck_ingredient_units_factor CHECK (to_base_factor > 0),
    CONSTRAINT ck_ingredient_units_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_ingredient_units_lookup
    ON ingredient_units(school_id, canteen_id, ingredient_id, status);

INSERT INTO ingredient_units (
    school_id, canteen_id, ingredient_id, unit_code, base_unit, to_base_factor, status
)
SELECT school_id, canteen_id, ingredient_id, base_unit, base_unit, 1, status
FROM ingredients;

CREATE TABLE procurement_plans (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    plan_no VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    idempotency_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, plan_id),
    CONSTRAINT uk_procurement_plans_scope_no UNIQUE (school_id, canteen_id, plan_no),
    CONSTRAINT uk_procurement_plans_scope_idempotency
        UNIQUE (school_id, canteen_id, idempotency_key),
    CONSTRAINT ck_procurement_plans_dates CHECK (period_end >= period_start),
    CONSTRAINT ck_procurement_plans_status CHECK (
        status IN ('DRAFT', 'CONFIRMED', 'CONVERTED', 'CANCELLED')
    )
);

CREATE INDEX idx_procurement_plans_scope_status_date
    ON procurement_plans(school_id, canteen_id, status, period_start, period_end);

CREATE TABLE procurement_plan_menus (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, plan_id, menu_id)
);

CREATE TABLE procurement_plan_items (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    ingredient_id VARCHAR(64) NOT NULL,
    required_quantity_base DECIMAL(19, 4) NOT NULL,
    inventory_quantity_base DECIMAL(19, 4) NOT NULL DEFAULT 0,
    open_order_quantity_base DECIMAL(19, 4) NOT NULL DEFAULT 0,
    shortage_quantity_base DECIMAL(19, 4) NOT NULL DEFAULT 0,
    planned_quantity_base DECIMAL(19, 4) NOT NULL DEFAULT 0,
    base_unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, plan_id, ingredient_id),
    CONSTRAINT ck_procurement_plan_items_required CHECK (required_quantity_base > 0),
    CONSTRAINT ck_procurement_plan_items_inventory CHECK (inventory_quantity_base >= 0),
    CONSTRAINT ck_procurement_plan_items_open_order CHECK (open_order_quantity_base >= 0),
    CONSTRAINT ck_procurement_plan_items_shortage CHECK (shortage_quantity_base >= 0),
    CONSTRAINT ck_procurement_plan_items_planned CHECK (planned_quantity_base >= 0)
);

CREATE INDEX idx_procurement_plan_items_ingredient
    ON procurement_plan_items(school_id, canteen_id, ingredient_id, plan_id);

CREATE TABLE procurement_plan_orders (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, plan_id),
    CONSTRAINT uk_procurement_plan_orders_order UNIQUE (school_id, canteen_id, order_id),
    CONSTRAINT uk_procurement_plan_orders_idempotency
        UNIQUE (school_id, canteen_id, idempotency_key)
);
