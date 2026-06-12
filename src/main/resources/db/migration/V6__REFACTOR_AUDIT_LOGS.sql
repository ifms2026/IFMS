-- ============================================================
-- V6: Refactor audit_logs for Hibernate Data-level Auditing
-- ============================================================
-- Thay đổi:
--   1. Thêm cột trace_id (VARCHAR 36 = UUID) — gom nhóm log theo request
--   2. Migrate dữ liệu action cũ → chuẩn hóa về INSERT / UPDATE / DELETE
--   3. Tạo 2 index tối ưu truy vấn Admin Dashboard
-- ============================================================

-- 1. Thêm cột trace_id
-- -------------------------------------------------------------
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(36);

-- 2. Chuẩn hóa cột action về 3 giá trị: INSERT | UPDATE | DELETE
-- -------------------------------------------------------------
-- Map các giá trị service-level cũ → giá trị Hibernate-level mới.
-- Những action liên quan tạo mới entity  → INSERT
-- Những action liên quan cập nhật entity → UPDATE
-- Những action liên quan xoá entity      → DELETE
-- Giá trị không xác định                 → UPDATE (safe default)

UPDATE audit_logs
SET action = CASE
    WHEN action IN ('USER_CREATED', 'DEPARTMENT_CREATED') THEN 'INSERT'
    WHEN action IN ('USER_LOCKED', 'USER_UNLOCKED', 'USER_UPDATED',
                    'BANK_INFO_UPDATED', 'ROLE_ASSIGNED', 'ROLE_REVOKED',
                    'PERMISSION_GRANTED', 'PERMISSION_REVOKED',
                    'DEPARTMENT_UPDATED', 'QUOTA_TOPUP', 'QUOTA_ADJUSTED',
                    'CONFIG_UPDATED', 'SYSTEM_FUND_ADJUSTED',
                    'PIN_RESET', 'PIN_LOCKED',
                    'USER_LOGIN_SUCCESS', 'USER_LOGIN_FAILED',
                    'DATA_EXPORTED', 'MANUAL_ADJUSTMENT',
                    'DB_INSERT', 'DB_UPDATE', 'DB_DELETE') THEN
        CASE
            WHEN action = 'DB_INSERT' THEN 'INSERT'
            WHEN action = 'DB_DELETE' OR action = 'DEPARTMENT_DELETED' THEN 'DELETE'
            ELSE 'UPDATE'
        END
    ELSE 'UPDATE'
END
WHERE action NOT IN ('INSERT', 'UPDATE', 'DELETE');

-- 3. Tạo index tối ưu truy vấn
-- -------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_audit_trace
    ON audit_logs (trace_id);

CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON audit_logs (entity_name, entity_id);
