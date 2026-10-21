CREATE TABLE IF NOT EXISTS excel_import_batches (
    id VARCHAR(36) PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    dataset VARCHAR(60) NOT NULL,
    scope_key VARCHAR(80) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    result TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    committed_at TIMESTAMPTZ,
    version BIGINT,
    CONSTRAINT uk_excel_batch_file UNIQUE(actor_id,dataset,scope_key,file_hash,schema_version)
);
CREATE INDEX IF NOT EXISTS idx_excel_batch_actor ON excel_import_batches(actor_id,created_at);
