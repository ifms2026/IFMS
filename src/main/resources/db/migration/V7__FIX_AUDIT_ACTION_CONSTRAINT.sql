-- ============================================================
-- V7: Fix audit_logs_action_check constraint
-- ============================================================
-- Vấn đề: Hibernate 6 tự sinh CHECK constraint cho cột @Enumerated(STRING)
-- chứa các giá trị enum cũ (USER_CREATED, USER_LOCKED...).
-- V6 đã migrate data nhưng chưa DROP constraint cũ → vi phạm khi INSERT
-- các giá trị mới INSERT/UPDATE/DELETE.
-- ============================================================

-- 1. Drop constraint cũ do Hibernate 6 tự tạo lúc init schema
ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_action_check;

-- 2. Tạo lại constraint chỉ cho phép 3 giá trị mới
ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_action_check
        CHECK (action IN ('INSERT', 'UPDATE', 'DELETE'));
