-- =============================================================================
-- IFMS — Full Data Reset Script
-- Xóa toàn bộ data (giữ nguyên schema). Chạy script này TRƯỚC khi restart app.
-- App restart → DataInitializer sẽ tự seed lại 9 accounts + configs.
--
-- Cách chạy:
--   psql -U IFMSadmin -d IFMSdb -f reset_data.sql
-- hoặc paste vào pgAdmin Query Tool rồi Execute.
-- =============================================================================

BEGIN;

-- 1. Phá circular FK: departments.manager_id → users trước
UPDATE departments SET manager_id = NULL;

-- 2. Truncate toàn bộ data tables (CASCADE xử lý FK còn lại)
--    flyway_schema_history KHÔNG xóa — giữ nguyên migration history
TRUNCATE TABLE
    audit_logs,
    notifications,
    deposit_logs,
    withdrawal_requests,
    advance_balances,
    ledger_entries,
    transactions,
    request_attachments,
    request_histories,
    requests,
    payslips,
    payroll_periods,
    phase_category_budgets,
    project_members,
    project_phases,
    projects,
    wallets,
    user_security_settings,
    user_profiles,
    file_storages,
    company_funds,
    system_configs,
    expense_categories,
    departments,
    role_permissions,
    roles,
    users
    RESTART IDENTITY CASCADE;

COMMIT;

-- Xác nhận
SELECT 'Reset hoàn tất. Restart Spring Boot app để DataInitializer seed lại data.' AS status;
