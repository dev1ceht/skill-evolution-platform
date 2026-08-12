-- Phase 3 moves menu, recipe and inventory state behind the same
-- school/canteen identity already used by the ledger module.

ALTER TABLE canteens
    ADD CONSTRAINT uk_canteen_school_id UNIQUE (school_id, id);

ALTER TABLE inventory_receipts RENAME TO legacy_inventory_receipts;
ALTER TABLE recipe_requirements RENAME TO legacy_recipe_requirements;
ALTER TABLE menus RENAME TO legacy_menus;
ALTER TABLE inventory RENAME TO legacy_inventory;

CREATE TABLE menus (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    decision_comment VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (school_id, canteen_id, id),
    CONSTRAINT fk_menu_canteen
        FOREIGN KEY (school_id, canteen_id) REFERENCES canteens(school_id, id)
);

CREATE TABLE recipe_requirements (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    material_id VARCHAR(64) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, menu_id, material_id),
    CONSTRAINT fk_recipe_menu_scope
        FOREIGN KEY (school_id, canteen_id, menu_id)
        REFERENCES menus(school_id, canteen_id, id)
);

CREATE TABLE inventory (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    material_id VARCHAR(64) NOT NULL,
    quantity_base DECIMAL(19, 4) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (school_id, canteen_id, material_id),
    CONSTRAINT fk_inventory_canteen
        FOREIGN KEY (school_id, canteen_id) REFERENCES canteens(school_id, id)
);

CREATE TABLE inventory_receipts (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    material_id VARCHAR(64) NOT NULL,
    request_quantity DECIMAL(19, 4) NOT NULL,
    request_unit VARCHAR(16) NOT NULL,
    quantity_base_after DECIMAL(19, 4),
    base_unit VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, idempotency_key),
    CONSTRAINT fk_receipt_canteen
        FOREIGN KEY (school_id, canteen_id) REFERENCES canteens(school_id, id)
);

INSERT INTO menus (school_id, canteen_id, id, status, decision_comment, version)
SELECT 'SCHOOL-001', 'CANTEEN-001', id, status, decision_comment, version
FROM legacy_menus;

INSERT INTO recipe_requirements (
    school_id, canteen_id, menu_id, material_id, quantity, unit
)
SELECT 'SCHOOL-001', 'CANTEEN-001', menu_id, material_id, quantity, unit
FROM legacy_recipe_requirements;

INSERT INTO inventory (
    school_id, canteen_id, material_id, quantity_base, base_unit
)
SELECT 'SCHOOL-001', 'CANTEEN-001', material_id, quantity_base, base_unit
FROM legacy_inventory;

INSERT INTO inventory_receipts (
    school_id, canteen_id, idempotency_key, material_id,
    request_quantity, request_unit, quantity_base_after, base_unit, created_at
)
SELECT
    'SCHOOL-001', 'CANTEEN-001', idempotency_key, material_id,
    request_quantity, request_unit, quantity_base_after, base_unit, created_at
FROM legacy_inventory_receipts;

DROP TABLE legacy_inventory_receipts;
DROP TABLE legacy_recipe_requirements;
DROP TABLE legacy_menus;
DROP TABLE legacy_inventory;
