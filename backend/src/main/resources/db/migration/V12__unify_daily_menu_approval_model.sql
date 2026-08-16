-- Phase 4: make daily_menus the canonical menu write aggregate.
-- The legacy menus table remains available to compatibility endpoints, but no
-- new Agent write path targets it.

ALTER TABLE daily_menus
    ADD submitted_by VARCHAR(64) NULL;

ALTER TABLE daily_menus
    ADD decision_by VARCHAR(64) NULL;

ALTER TABLE daily_menus
    ADD decision_comment VARCHAR(500) NULL;

ALTER TABLE daily_menus
    ADD published_by VARCHAR(64) NULL;

ALTER TABLE daily_menus
    DROP CONSTRAINT ck_daily_menu_status;

ALTER TABLE daily_menus
    ADD CONSTRAINT ck_daily_menu_status CHECK (
        status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PUBLISHED')
    );

CREATE INDEX idx_daily_menus_scope_status
    ON daily_menus(school_id, canteen_id, status, menu_date, menu_id);
