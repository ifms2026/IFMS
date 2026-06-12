-- =============================================================
-- V4: Add token_version column to users table (Single-session login)
-- =============================================================
-- Mỗi lần login, tokenVersion tăng 1.
-- JWT chứa claim "ver" — nếu ver < tokenVersion → session bị huỷ.

ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- Drop redundant Spring Security boolean columns
ALTER TABLE users DROP COLUMN enabled;
ALTER TABLE users DROP COLUMN account_non_expired;
ALTER TABLE users DROP COLUMN account_non_locked;
ALTER TABLE users DROP COLUMN credentials_non_expired;
