CREATE TABLE excel_export_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    dataset_code VARCHAR(100) NOT NULL,
    scope_key VARCHAR(100) NOT NULL,
    filter_snapshot TEXT NOT NULL,
    row_count INTEGER NOT NULL CHECK (row_count >= 0),
    file_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_excel_export_actor_created ON excel_export_logs(actor_id, created_at DESC);
