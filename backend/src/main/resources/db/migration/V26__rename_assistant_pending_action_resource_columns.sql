-- The pending-action aggregate stores resources for menu, procurement, inventory and alert intents.
-- Rename the original menu-only column names now that all callers use the generic resource fields.
ALTER TABLE assistant_pending_actions
    DROP CONSTRAINT ck_assistant_pending_action_versions;
ALTER TABLE assistant_pending_actions RENAME COLUMN menu_id TO resource_id;
ALTER TABLE assistant_pending_actions RENAME COLUMN menu_version TO resource_version;
ALTER TABLE assistant_pending_actions
    ADD CONSTRAINT ck_assistant_pending_action_versions
        CHECK (run_version >= 0 AND resource_version >= 0);
