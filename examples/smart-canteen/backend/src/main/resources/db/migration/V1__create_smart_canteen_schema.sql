CREATE TABLE menus (
    id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    decision_comment VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE recipe_requirements (
    menu_id VARCHAR(64) NOT NULL,
    material_id VARCHAR(64) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (menu_id, material_id),
    CONSTRAINT fk_recipe_menu FOREIGN KEY (menu_id) REFERENCES menus(id)
);

CREATE TABLE inventory (
    material_id VARCHAR(64) PRIMARY KEY,
    quantity_base DECIMAL(19, 4) NOT NULL,
    base_unit VARCHAR(16) NOT NULL
);

CREATE TABLE inventory_receipts (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    material_id VARCHAR(64) NOT NULL,
    request_quantity DECIMAL(19, 4) NOT NULL,
    request_unit VARCHAR(16) NOT NULL,
    quantity_base_after DECIMAL(19, 4),
    base_unit VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledger_requirements (
    ledger_code VARCHAR(64) PRIMARY KEY,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO menus (id, status, decision_comment, version)
VALUES ('MENU-001', 'DRAFT', NULL, 0);

INSERT INTO recipe_requirements (menu_id, material_id, quantity, unit)
VALUES ('MENU-001', 'FLOUR', 2.0000, 'kg');

INSERT INTO recipe_requirements (menu_id, material_id, quantity, unit)
VALUES ('MENU-001', 'EGG', 12.0000, 'count');

INSERT INTO inventory (material_id, quantity_base, base_unit)
VALUES ('FLOUR', 500.0000, 'g');

INSERT INTO inventory (material_id, quantity_base, base_unit)
VALUES ('EGG', 20.0000, 'count');

INSERT INTO ledger_requirements (ledger_code, completed)
VALUES ('PURCHASE_ACCEPTANCE', FALSE);
