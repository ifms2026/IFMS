package com.mkwang.backend.modules.request.entity;

/**
 * Enum representing the type of a request.
 *
 * Flow 1 — Personal expense (Member → Team Leader → Accountant):
 *   ADVANCE, EXPENSE, REIMBURSE
 *
 * Flow 2 — Project fund top-up (Team Leader → Manager → Auto):
 *   PROJECT_TOPUP
 *
 * Flow 3 — Department quota top-up (Manager → CFO → Auto):
 *   DEPARTMENT_TOPUP
 */
public enum RequestType {
  ADVANCE,          // Tạm ứng — Member xin ứng tiền vào ví cá nhân
  EXPENSE,          // Thanh toán chi phí — Member xin thanh toán trực tiếp cho nhà cung cấp
  REIMBURSE,        // Hoàn ứng — Member nộp hóa đơn để quyết toán khoản tạm ứng
  PROJECT_TOPUP,    // Xin cấp vốn Dự án — Team Leader gửi Manager (Dept Fund → Project Fund)
  DEPARTMENT_TOPUP  // Xin cấp quota Phòng ban — Manager gửi CFO (System Fund → Dept Fund)
}
