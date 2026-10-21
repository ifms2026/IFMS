-- Do not rewrite legacy evidence: nullable dimensions explicitly mean unknown history.
ALTER TABLE accounting_journals
    ADD COLUMN IF NOT EXISTS business_date DATE,
    ADD COLUMN IF NOT EXISTS department_id BIGINT,
    ADD COLUMN IF NOT EXISTS department_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS phase_id BIGINT,
    ADD COLUMN IF NOT EXISTS phase_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS category_id BIGINT,
    ADD COLUMN IF NOT EXISTS category_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS source_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS original_journal_id BIGINT REFERENCES accounting_journals(id),
    ADD COLUMN IF NOT EXISTS creation_xid BIGINT;
-- Only future headers receive the transaction identity; old journals remain sealed.
ALTER TABLE accounting_journals ALTER COLUMN creation_xid SET DEFAULT txid_current();
ALTER TABLE accounting_journal_lines
    ADD COLUMN IF NOT EXISTS department_id BIGINT,
    ADD COLUMN IF NOT EXISTS phase_id BIGINT,
    ADD COLUMN IF NOT EXISTS category_id BIGINT,
    ADD COLUMN IF NOT EXISTS project_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS phase_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS category_name VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_journal_line_scope ON accounting_journal_lines(project_id, phase_id, category_id);
CREATE INDEX IF NOT EXISTS idx_journal_line_account ON accounting_journal_lines(account_code, journal_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_journal_full_reversal ON accounting_journals(original_journal_id)
    WHERE event_type = 'REVERSAL';

CREATE OR REPLACE FUNCTION guard_journal_line_insert() RETURNS TRIGGER AS $$
DECLARE owner_xid BIGINT;
BEGIN
    SELECT creation_xid INTO owner_xid FROM accounting_journals WHERE id = NEW.journal_id FOR UPDATE;
    IF owner_xid IS NULL OR owner_xid <> txid_current() THEN
        RAISE EXCEPTION 'Posted journal is sealed; append a linked correction instead.' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_journal_line_insert_guard BEFORE INSERT ON accounting_journal_lines
    FOR EACH ROW EXECUTE FUNCTION guard_journal_line_insert();

CREATE OR REPLACE FUNCTION check_journal_final_balance() RETURNS TRIGGER AS $$
DECLARE target_id BIGINT; expected NUMERIC; debits NUMERIC; credits NUMERIC; line_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'accounting_journals' THEN target_id := NEW.id; ELSE target_id := NEW.journal_id; END IF;
    SELECT total_amount INTO expected FROM accounting_journals WHERE id = target_id;
    SELECT COUNT(*), COALESCE(SUM(debit_amount),0), COALESCE(SUM(credit_amount),0)
        INTO line_count, debits, credits FROM accounting_journal_lines WHERE journal_id = target_id;
    IF line_count < 2 OR debits <> credits OR debits <> expected THEN
        RAISE EXCEPTION 'Unbalanced or incomplete journal %', target_id USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END; $$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER trg_journal_balance_header AFTER INSERT ON accounting_journals
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_journal_final_balance();
CREATE CONSTRAINT TRIGGER trg_journal_balance_line AFTER INSERT ON accounting_journal_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_journal_final_balance();

CREATE TABLE IF NOT EXISTS accounting_periods (
    period VARCHAR(7) PRIMARY KEY, closed BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at TIMESTAMPTZ, closed_by BIGINT, close_reason TEXT
);
CREATE OR REPLACE FUNCTION guard_journal_open_period() RETURNS TRIGGER AS $$
BEGIN
    -- Same lock is used by the close operation; protects the check/insert race.
    PERFORM pg_advisory_xact_lock(hashtextextended('IFMS_ACCOUNTING_PERIOD:' || NEW.posting_period, 0));
    IF NEW.posting_period <> to_char(NEW.posting_date, 'YYYY-MM') THEN
        RAISE EXCEPTION 'Posting date does not belong to posting period' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (SELECT 1 FROM accounting_periods WHERE period = NEW.posting_period AND closed) THEN
        RAISE EXCEPTION 'Accounting period is closed' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_journal_open_period BEFORE INSERT ON accounting_journals
    FOR EACH ROW EXECUTE FUNCTION guard_journal_open_period();
