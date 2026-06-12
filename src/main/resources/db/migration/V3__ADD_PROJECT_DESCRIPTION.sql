-- V3: Add description column to projects table
-- Required by new API design: Manager can provide description when creating a project.

ALTER TABLE projects ADD COLUMN description TEXT;
