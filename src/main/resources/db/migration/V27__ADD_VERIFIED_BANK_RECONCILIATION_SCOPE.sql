ALTER TABLE company_funds
    ADD COLUMN IF NOT EXISTS bank_scope_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS bank_scope_evidence TEXT,
    ADD COLUMN IF NOT EXISTS statement_evidence_ref TEXT,
    ADD COLUMN IF NOT EXISTS statement_as_of TIMESTAMPTZ;
