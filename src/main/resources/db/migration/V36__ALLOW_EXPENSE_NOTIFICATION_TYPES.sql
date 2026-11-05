-- Notifications are persisted asynchronously. Existing installations may
-- retain an enum CHECK from the Hibernate baseline which predates the
-- separate expense verification and payment events.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'REQUEST_SUBMITTED', 'REQUEST_APPROVED_BY_TL', 'REQUEST_REJECTED',
    'REQUEST_PAID', 'EXPENSE_VERIFIED', 'EXPENSE_PAID',
    'PROJECT_TOPUP_APPROVED', 'PROJECT_TOPUP_REJECTED',
    'DEPT_TOPUP_APPROVED', 'DEPT_TOPUP_REJECTED',
    'SALARY_PAID', 'SYSTEM', 'SECURITY_ALERT'
));
