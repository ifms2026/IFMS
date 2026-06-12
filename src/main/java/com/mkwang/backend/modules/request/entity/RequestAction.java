package com.mkwang.backend.modules.request.entity;

/**
 * Enum representing actions that can be taken on a request.
 * NO ESCALATE — abolished completely.
 */
public enum RequestAction {
  APPROVE,  // Duyệt yêu cầu (Team Leader / Manager / Admin tùy luồng)
  REJECT,   // Từ chối yêu cầu
  PAYOUT,   // Accountant giải ngân (Luồng 1 only)
  CANCEL    // Người tạo tự hủy (chỉ khi đang PENDING_APPROVAL)
}
