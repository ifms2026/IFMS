CREATE TABLE IF NOT EXISTS financial_operation_receipts (
    actor_id BIGINT NOT NULL REFERENCES users(id),
    operation VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    response TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(actor_id,operation,idempotency_key)
);
CREATE TRIGGER trg_operation_receipts_append_only BEFORE UPDATE OR DELETE ON financial_operation_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_accounting_journal_mutation();
