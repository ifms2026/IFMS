-- Historical counter corrections are append-only and do not create wallet or journal movements.
CREATE TABLE accounting_cost_reconciliations (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    before_snapshot JSONB NOT NULL,
    after_snapshot JSONB NOT NULL,
    source_count INTEGER NOT NULL,
    reason TEXT NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cost_counter_reconciliations_project ON accounting_cost_reconciliations(project_id,created_at);
