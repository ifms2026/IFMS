-- Existing installations baselined through V16 can retain Hibernate-generated
-- enum checks which do not exist on a database built only from the SQL scripts.
-- Keep those checks, but allow the complete current accounting lifecycle.
ALTER TABLE requests DROP CONSTRAINT IF EXISTS requests_status_check;
ALTER TABLE requests ADD CONSTRAINT requests_status_check CHECK (status IN (
    'PENDING', 'APPROVED_BY_TEAM_LEADER', 'ACCOUNTANT_VERIFIED',
    'APPROVED_BY_MANAGER', 'APPROVED_BY_CFO', 'PAID', 'REJECTED', 'REVERSED', 'CANCELLED'
));

ALTER TABLE request_histories DROP CONSTRAINT IF EXISTS request_histories_action_check;
ALTER TABLE request_histories ADD CONSTRAINT request_histories_action_check CHECK (action IN (
    'APPROVE', 'REJECT', 'VERIFY', 'PAYOUT', 'REVERSE', 'CANCEL'
));

ALTER TABLE request_histories DROP CONSTRAINT IF EXISTS request_histories_status_after_action_check;
ALTER TABLE request_histories ADD CONSTRAINT request_histories_status_after_action_check
CHECK (status_after_action IN (
    'PENDING', 'APPROVED_BY_TEAM_LEADER', 'ACCOUNTANT_VERIFIED',
    'APPROVED_BY_MANAGER', 'APPROVED_BY_CFO', 'PAID', 'REJECTED', 'REVERSED', 'CANCELLED'
));

ALTER TABLE payslips DROP CONSTRAINT IF EXISTS payslips_status_check;
ALTER TABLE payslips ADD CONSTRAINT payslips_status_check CHECK (status IN ('DRAFT', 'PAID', 'REVERSED'));
