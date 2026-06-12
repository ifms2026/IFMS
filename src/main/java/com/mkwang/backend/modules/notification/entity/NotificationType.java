package com.mkwang.backend.modules.notification.entity;

/**
 * Notification types — gắn với từng domain event trong IFMS.
 *
 * Flow 1 (ADVANCE / EXPENSE / REIMBURSE): Member → Team Leader (duyệt) → Accountant (giải ngân)
 * Flow 2 (PROJECT_TOPUP):                 Team Leader (request) → Manager (duyệt) → Auto-pay
 * Flow 3 (DEPARTMENT_TOPUP):              Manager (request) → CFO (duyệt) → Auto-pay
 * Payroll:                                Accountant execute → Employee (notify when salary arrives)
 */
public enum NotificationType {

    // ─── Flow 1: Personal Expense (Member → Team Leader → Accountant) ────────
    REQUEST_SUBMITTED,              // Member tạo request → notify Team Leader cần duyệt
    REQUEST_APPROVED_BY_TL,         // Team Leader duyệt → notify Accountant (kế toán) cần giải ngân
    REQUEST_REJECTED,               // Bị từ chối (bất kỳ stage) → notify requester
    REQUEST_PAID,                   // Accountant đã giải ngân → notify requester

    // ─── Flow 2: Project Fund Top-up ───────────────────────────────────────
    PROJECT_TOPUP_APPROVED,         // Manager duyệt + auto-pay hoàn tất → notify TL
    PROJECT_TOPUP_REJECTED,         // Manager từ chối → notify TL

    // ─── Flow 3: Department Quota Top-up ───────────────────────────────────
    DEPT_TOPUP_APPROVED,            // CFO duyệt + auto-pay hoàn tất → notify Manager
    DEPT_TOPUP_REJECTED,            // CFO từ chối → notify Manager

    // ─── Payroll ───────────────────────────────────────────────────────────
    SALARY_PAID,                    // Lương đã được chuyển khoản vào tài khoản → notify employee

    // ─── System ────────────────────────────────────────────────────────────
    SYSTEM,                         // Thông báo hệ thống chung (maintenance, announcements)
    SECURITY_ALERT                  // Cảnh báo bảo mật (PIN bị khóa, đăng nhập lạ, v.v.)
}
