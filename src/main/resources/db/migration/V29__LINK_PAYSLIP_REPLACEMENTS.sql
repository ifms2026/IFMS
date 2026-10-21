ALTER TABLE payslips ADD COLUMN IF NOT EXISTS replaces_payslip_id BIGINT UNIQUE REFERENCES payslips(id);
