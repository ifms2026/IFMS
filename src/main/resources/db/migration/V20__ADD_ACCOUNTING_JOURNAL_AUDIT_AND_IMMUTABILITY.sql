-- Preserve actor/time for new entries. Legacy rows intentionally keep null because
-- their original actor and creation timestamp cannot be reconstructed reliably.
ALTER TABLE accounting_journals
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_accounting_journal_employee ON accounting_journals (employee_id);
CREATE INDEX IF NOT EXISTS idx_accounting_journal_project ON accounting_journals (project_id);
CREATE INDEX IF NOT EXISTS idx_accounting_journal_created_at ON accounting_journals (created_at);

CREATE OR REPLACE FUNCTION reject_accounting_journal_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Accounting journals are append-only; create a linked correction instead.'
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_accounting_journals_append_only ON accounting_journals;
CREATE TRIGGER trg_accounting_journals_append_only
    BEFORE UPDATE OR DELETE ON accounting_journals
    FOR EACH ROW EXECUTE FUNCTION reject_accounting_journal_mutation();

DROP TRIGGER IF EXISTS trg_accounting_journal_lines_append_only ON accounting_journal_lines;
CREATE TRIGGER trg_accounting_journal_lines_append_only
    BEFORE UPDATE OR DELETE ON accounting_journal_lines
    FOR EACH ROW EXECUTE FUNCTION reject_accounting_journal_mutation();
