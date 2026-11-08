-- Department.total_project_quota/total_available_balance were dead columns until the
-- CFO/Manager approval flows were wired to maintain them (see application code:
-- DepartmentService.receiveQuota/allocateToProject, called from RequestServiceImpl's
-- approveCfoRequest/approveManagerRequest). Any department that received real
-- DEPARTMENT_TOPUP/PROJECT_TOPUP approvals before that wiring existed has stale values
-- here that no longer match the real money movement recorded in wallets/projects.
--
-- Recompute both fields from ground truth:
--   total_available_balance = the department's current DEPARTMENT wallet balance.
--   total_project_quota     = that same balance, plus everything already allocated out
--                              to the department's projects (Σ project.total_budget) —
--                              i.e. every dong the department has ever received, whether
--                              it is still sitting in the department wallet or has since
--                              been funded into a project.
UPDATE departments d
SET total_available_balance = COALESCE(
        (SELECT w.balance FROM wallets w WHERE w.owner_type = 'DEPARTMENT' AND w.owner_id = d.id),
        0),
    total_project_quota = COALESCE(
        (SELECT w.balance FROM wallets w WHERE w.owner_type = 'DEPARTMENT' AND w.owner_id = d.id),
        0)
        + COALESCE((SELECT SUM(p.total_budget) FROM projects p WHERE p.department_id = d.id), 0);
