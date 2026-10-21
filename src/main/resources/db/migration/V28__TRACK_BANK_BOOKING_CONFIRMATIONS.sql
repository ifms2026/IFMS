CREATE TABLE IF NOT EXISTS bank_booking_confirmations (
    statement_line_id BIGINT PRIMARY KEY REFERENCES bank_statement_lines(id),
    booking_date DATE NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    evidence_ref TEXT NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_bank_booking_confirmation_append_only BEFORE UPDATE OR DELETE ON bank_booking_confirmations
    FOR EACH ROW EXECUTE FUNCTION reject_accounting_journal_mutation();
