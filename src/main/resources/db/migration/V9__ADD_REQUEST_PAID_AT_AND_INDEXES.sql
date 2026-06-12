-- =============================================================================
-- V9: Add paid_at to requests + missing indexes for common query patterns
-- =============================================================================

-- paid_at: records when a request transitioned to PAID status.
-- Enables SLA reporting and overdue-disbursement queries without joining request_histories.
ALTER TABLE requests ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITHOUT TIME ZONE;

-- "My requests" — most frequent query for any user
CREATE INDEX IF NOT EXISTS idx_requests_requester      ON requests (requester_id, status);

-- Accountant dashboard — filter pending disbursements by type
CREATE INDEX IF NOT EXISTS idx_requests_status_type    ON requests (status, type);

-- Project manager view — all requests under a project filtered by status
CREATE INDEX IF NOT EXISTS idx_requests_project_status ON requests (project_id, status);
