-- =============================================================================
-- V11: Fix seq_employee_code to account for newly seeded users
-- =============================================================================
-- V2 set START WITH 8 when only MK000–MK007 were seeded.
-- DataInitializer now seeds two additional users:
--   MK008 = teamLeadIT  (Hoàng Minh Tuấn)
--   MK010 = CFO         (Nguyễn Văn Minh)
--
-- Without this fix, the first employee created via API would receive MK008,
-- conflicting with the existing teamLeadIT record.
--
-- RESTART WITH 11: skip MK008 (used), MK009 (unused gap), MK010 (used).
-- New employees created via API will start from MK011.
-- =============================================================================

ALTER SEQUENCE seq_employee_code RESTART WITH 11;
