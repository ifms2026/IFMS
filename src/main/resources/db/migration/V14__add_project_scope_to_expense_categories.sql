-- V14: Allow expense categories to be scoped per project.
-- System-wide categories (project_id IS NULL) are visible to everyone.
-- Project-specific categories (project_id IS NOT NULL) are visible only within that project.

-- 1. Drop the global unique constraint on name (was enforced globally, now per-scope)
ALTER TABLE expense_categories
    DROP CONSTRAINT IF EXISTS uc_expense_categories_name;

-- 2. Add nullable project_id FK column
ALTER TABLE expense_categories
    ADD COLUMN IF NOT EXISTS project_id BIGINT;

ALTER TABLE expense_categories
    ADD CONSTRAINT fk_expense_cat_on_project
        FOREIGN KEY (project_id) REFERENCES projects (id);

-- 3. Partial unique indexes replace the old global unique constraint
--    System-wide: name must be unique among categories where project_id IS NULL
CREATE UNIQUE INDEX IF NOT EXISTS uidx_expense_cat_name_system
    ON expense_categories (name)
    WHERE project_id IS NULL;

--    Project-scoped: name must be unique within each project
CREATE UNIQUE INDEX IF NOT EXISTS uidx_expense_cat_name_project
    ON expense_categories (name, project_id)
    WHERE project_id IS NOT NULL;
