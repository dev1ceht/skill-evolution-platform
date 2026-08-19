-- Remove the pre-daily-menu compatibility model after all callers have migrated.
-- recipe_requirements must be dropped before menus because it owns a foreign key to menus.
DROP TABLE IF EXISTS recipe_requirements;
DROP TABLE IF EXISTS menus;
