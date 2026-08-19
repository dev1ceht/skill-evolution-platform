-- Finalize the removal of the pre-operational compatibility data model.
-- V2 already copied ledger_requirements into the scoped ledger tables and V5
-- introduced inventory_batches/purchase_receipts as the canonical model.
DROP TABLE IF EXISTS inventory_receipts;
DROP TABLE IF EXISTS ledger_requirements;

-- Convert any legacy MENU-* identifiers that reached the canonical tables.
-- MENU-001 is the original seed; other values receive deterministic, ordered
-- six-digit short IDs. A temporary mapping table keeps every reference in sync.
DROP TABLE IF EXISTS menu_id_backfill_v27;
CREATE TABLE menu_id_backfill_v27 (
    old_menu_id VARCHAR(64) NOT NULL,
    new_menu_id VARCHAR(7) NOT NULL,
    PRIMARY KEY (old_menu_id),
    CONSTRAINT uk_menu_id_backfill_new UNIQUE (new_menu_id)
);

INSERT INTO menu_id_backfill_v27 (old_menu_id, new_menu_id)
SELECT old_menu_id,
       CASE
           WHEN old_menu_id = 'MENU-001' THEN 'M001'
           ELSE CONCAT('M', LPAD(ROW_NUMBER() OVER (ORDER BY old_menu_id), 6, '0'))
       END
FROM (
    SELECT menu_id AS old_menu_id
    FROM daily_menus
    WHERE menu_id LIKE 'MENU-%'
    UNION
    SELECT menu_id AS old_menu_id
    FROM daily_menu_items
    WHERE menu_id LIKE 'MENU-%'
    UNION
    SELECT menu_id AS old_menu_id
    FROM procurement_plan_menus
    WHERE menu_id LIKE 'MENU-%'
    UNION
    SELECT resource_id AS old_menu_id
    FROM assistant_pending_actions
    WHERE intent = 'menu.publish'
      AND resource_id LIKE 'MENU-%'
) legacy_menu_ids;

UPDATE daily_menu_items
SET menu_id = (
    SELECT new_menu_id
    FROM menu_id_backfill_v27
    WHERE old_menu_id = daily_menu_items.menu_id
)
WHERE menu_id IN (SELECT old_menu_id FROM menu_id_backfill_v27);

UPDATE procurement_plan_menus
SET menu_id = (
    SELECT new_menu_id
    FROM menu_id_backfill_v27
    WHERE old_menu_id = procurement_plan_menus.menu_id
)
WHERE menu_id IN (SELECT old_menu_id FROM menu_id_backfill_v27);

UPDATE assistant_pending_actions
SET resource_id = (
    SELECT new_menu_id
    FROM menu_id_backfill_v27
    WHERE old_menu_id = assistant_pending_actions.resource_id
)
WHERE intent = 'menu.publish'
  AND resource_id IN (SELECT old_menu_id FROM menu_id_backfill_v27);

UPDATE daily_menus
SET menu_id = (
    SELECT new_menu_id
    FROM menu_id_backfill_v27
    WHERE old_menu_id = daily_menus.menu_id
)
WHERE menu_id IN (SELECT old_menu_id FROM menu_id_backfill_v27);

DROP TABLE menu_id_backfill_v27;

ALTER TABLE daily_menus
    ADD CONSTRAINT ck_daily_menu_id_short
        CHECK (menu_id REGEXP '^M([0-9]{3}|[A-Fa-f0-9]{6})$');

ALTER TABLE daily_menu_items
    ADD CONSTRAINT ck_daily_menu_item_id_short
        CHECK (menu_id REGEXP '^M([0-9]{3}|[A-Fa-f0-9]{6})$');

ALTER TABLE procurement_plan_menus
    ADD CONSTRAINT ck_procurement_plan_menu_id_short
        CHECK (menu_id REGEXP '^M([0-9]{3}|[A-Fa-f0-9]{6})$');

ALTER TABLE assistant_pending_actions
    ADD CONSTRAINT ck_assistant_pending_menu_id_short
        CHECK (intent <> 'menu.publish'
            OR resource_id REGEXP '^M([0-9]{3}|[A-Fa-f0-9]{6})$');
