package com.mkwang.backend.modules.request.entity;

/**
 * Status lifecycle of a request in the approval workflow (Option 2b+ — Segregation of Duties).
 *
 * Flow 1 — ADVANCE / EXPENSE / REIMBURSE (HIGH RISK):
 *   PENDING → APPROVED_BY_TEAM_LEADER → PAID
 *         ↘ REJECTED
 *         ↘ CANCELLED (only while PENDING)
 *   Sau khi TEAM_LEADER duyệt, request được xử lý thanh toán theo luồng nghiệp vụ hiện tại.
 *
 * Flow 2 — PROJECT_TOPUP (MEDIUM RISK):
 *   PENDING → APPROVED_BY_MANAGER → [Auto-pay scheduler] → PAID
 *         ↘ REJECTED
 *   Không cần Accountant duyệt (chỉ ghi sổ post-facto).
 *
 * Flow 3 — DEPARTMENT_TOPUP (LOW RISK):
 *   PENDING → APPROVED_BY_CFO → [Auto-pay scheduler] → PAID
 *         ↘ REJECTED
 *   Không cần Accountant duyệt (chỉ ghi sổ post-facto).
 *
 * Segregation of Duties: Decision (duyệt) tách biệt theo từng flow.
 */
public enum RequestStatus {

  // ─ Waiting for decision ─
  PENDING,
  // Dùng cho tất cả flows: Member vừa tạo request, chờ approver duyệt

  // ─ Flow 1: ADVANCE/EXPENSE/REIMBURSE ─
  APPROVED_BY_TEAM_LEADER,
  // TEAM_LEADER đã duyệt, chờ xử lý thanh toán theo flow

  // ─ Flow 2: PROJECT_TOPUP ─
  APPROVED_BY_MANAGER,
  // Manager đã duyệt, scheduler sẽ auto-pay trong vài phút

  // ─ Flow 3: DEPARTMENT_TOPUP ─
  APPROVED_BY_CFO,
  // CFO đã duyệt, scheduler sẽ auto-pay trong vài phút

  // ─ Terminal states ─
  PAID,
  // Đã giải ngân

  REJECTED,
  // Bị từ chối bởi approver (TL/Manager/CFO) hoặc Accountant (Flow 1 only)

  CANCELLED
  // Người tạo request tự hủy (chỉ được phép khi PENDING hoặc chưa lock funds)
}
