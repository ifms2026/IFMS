# Create next Flyway migration

**Description:** $ARGUMENTS

## Instructions

1. Read all files in `src/main/resources/db/migration/` and find the highest version number (currently up to V11).
2. The new file version is `V{highest + 1}`.
3. Derive a SHORT_DESCRIPTION from the arguments provided (UPPER_SNAKE_CASE, e.g. `ADD_USER_PROFILE_TABLE`).
4. Create the file at:
   ```
   src/main/resources/db/migration/V{N}__{SHORT_DESCRIPTION}.sql
   ```

### SQL conventions
- Use `IF NOT EXISTS` / `IF EXISTS` for safety.
- Column names in **snake_case**.
- Money columns: `NUMERIC(19, 2) NOT NULL DEFAULT 0`.
- Timestamps: `TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- Primary key: `id BIGSERIAL PRIMARY KEY` (or `BIGINT` with sequence if needed for business codes).
- Foreign keys: explicit `CONSTRAINT fk_... FOREIGN KEY (...) REFERENCES ...` with `ON DELETE` behavior stated.
- End the file with a blank line.

### Do NOT
- Modify any existing migration file.
- Use `ALTER TABLE` to change a column type without a clear cast.

After creating the file, print the full file path and its contents for review.
