package com.mkwang.backend.modules.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * System permissions used for Dynamic RBAC.
 *
 * Role → Permission mapping overview:
 * EMPLOYEE : personal profile, wallet, project view, request create/view
 * TEAM_LEADER : + approve Flow 1, manage project budget/phase/members
 * MANAGER : + create/manage projects, approve Flow 2, dept dashboard
 * ACCOUNTANT : + payout, payroll, system fund
 * CFO : financial governance — approve Flow 3, fund top-up, global dashboard
 * ADMIN : system configuration & IAM only — no financial approvals
 */
@Getter
@RequiredArgsConstructor
public enum Permission {

  // ================================================================
  // 1. PERSONAL (all authenticated users)
  // ================================================================
  USER_PROFILE_VIEW("Xem hồ sơ cá nhân"),
  USER_PROFILE_UPDATE("Cập nhật hồ sơ (Avatar, SĐT, Địa chỉ)"),
  USER_PIN_UPDATE("Thiết lập hoặc đổi mã PIN giao dịch"),
  NOTIFICATION_VIEW("Xem thông báo biến động số dư / lương"),

  // ================================================================
  // 2. IAM & SECURITY (Admin only)
  // ================================================================
  USER_VIEW_LIST("Xem danh sách nhân viên toàn hệ thống"),
  USER_CREATE("Cấp tài khoản mới (Onboarding)"),
  USER_UPDATE("Chỉnh sửa thông tin & điều chuyển nhân sự"),
  USER_LOCK("Khóa / Mở khóa tài khoản"),
  ROLE_MANAGE("Quản lý vai trò & phân quyền động"),

  // ================================================================
  // 3. WALLET (personal — all roles)
  // ================================================================
  WALLET_VIEW_SELF("Xem dashboard ví cá nhân (số dư, tiền khóa)"),
  WALLET_DEPOSIT("Nạp tiền vào ví qua payment gateway"),
  WALLET_WITHDRAW("Rút tiền từ ví về ngân hàng"),
  WALLET_TRANSACTION_VIEW("Xem lịch sử giao dịch cá nhân"),

  // ================================================================
  // 4. WALLET RISK CONTROL (Accountant / CFO)
  // ================================================================
  TRANSACTION_APPROVE_WITHDRAW("Duyệt lệnh rút tiền lớn hoặc bị treo (Pending)"),
  WALLET_VIEW_ALL("Xem số dư và lịch sử giao dịch của mọi loại ví (PROJECT, DEPARTMENT, COMPANY_FUND)"),

  // ================================================================
  // 5. PROJECT (read — Employee+; write — Team Leader / Manager)
  // ================================================================
  PROJECT_VIEW_ACTIVE("Xem dự án / phase đang active (để tạo request)"),

  PROJECT_CATEGORY_MANAGE("Quản lý danh mục chi tiêu của dự án"),
  PROJECT_BUDGET_ALLOCATE("Phân bổ ngân sách Phase / Category"),
  PROJECT_PHASE_MANAGE("Tạo, cấp vốn, đóng/mở Phase"),
  PROJECT_MEMBER_MANAGE("Thêm / xóa thành viên dự án"),

  PROJECT_CREATE("Khởi tạo dự án mới"),
  PROJECT_UPDATE("Cập nhật thông tin chung dự án"),
  PROJECT_STATUS_MANAGE("Tạm dừng hoặc đóng dự án"),
  PROJECT_ASSIGN_LEADER("Chỉ định / thay đổi Team Leader"),

  PROJECT_VIEW_ALL("Xem tất cả dự án (Accountant / CFO audit)"),

  // ================================================================
  // 6. REQUEST FLOW
  // Flow 1 (ADVANCE/EXPENSE/REIMBURSE) : Member → Team Leader → Accountant
  // Flow 2 (PROJECT_TOPUP) : Team Leader → Manager → Auto
  // Flow 3 (DEPARTMENT_TOPUP) : Manager → CFO → Auto
  // ================================================================
  REQUEST_CREATE("Tạo yêu cầu chi / ứng / hoàn ứng & upload chứng từ"),
  REQUEST_VIEW_SELF("Xem danh sách & trạng thái yêu cầu của chính mình"),

  // Team Leader — Flow 1
  REQUEST_APPROVE_TEAM_LEADER("Duyệt mọi yêu cầu chi tiêu của Member (Flow 1)"),

  // Manager — Flow 2
  REQUEST_VIEW_DEPT("Xem yêu cầu thuộc phòng ban mình quản lý"),
  REQUEST_APPROVE_PROJECT_TOPUP("Duyệt yêu cầu cấp vốn dự án của Team Leader (Flow 2)"),
  REQUEST_REJECT("Từ chối yêu cầu (bắt buộc nhập lý do)"),

  // CFO — Flow 3
  REQUEST_VIEW_ALL("Xem tất cả yêu cầu toàn hệ thống"),
  REQUEST_APPROVE_DEPT_TOPUP("Duyệt yêu cầu cấp quota phòng ban của Manager (Flow 3)"),

  // Accountant — payout
  REQUEST_VIEW_APPROVED("Xem yêu cầu đã duyệt đang chờ giải ngân"),
  REQUEST_PAYOUT("Thực hiện giải ngân (trừ quỹ → cộng ví nhân viên)"),

  // ================================================================
  // 7. PAYROLL & ACCOUNTING (Accountant)
  // ================================================================
  PAYROLL_VIEW_SELF("Xem kỳ lương & chi tiết phiếu lương cá nhân"),
  PAYROLL_DOWNLOAD("Tải phiếu lương PDF"),
  PAYROLL_MANAGE("Quản lý kỳ lương (tạo mới, upload Excel, validate)"),
  PAYROLL_EXECUTE("Chốt & chi lương hàng loạt (kèm auto-netting trừ nợ)"),

  // ================================================================
  // 8. COMPANY FUND (Accountant daily ops / CFO oversight)
  // ================================================================
  COMPANY_FUND_VIEW("Xem số dư quỹ công ty và báo cáo đối soát"),
  COMPANY_FUND_TOPUP("Nạp tiền vào quỹ công ty từ ngân hàng (SYSTEM_TOPUP)"),

  // ================================================================
  // 9. ORG STRUCTURE & CONFIG
  // ================================================================

  // Manager
  DEPT_VIEW_DASHBOARD("Xem dashboard phòng ban (ngân sách, báo cáo chi tiêu)"),

  // Admin — structural only
  DEPT_MANAGE("Quản lý danh sách phòng ban (tạo, sửa tên, mã phòng)"),
  SYSTEM_CONFIG_MANAGE("Cấu hình tham số hệ thống (hạn mức rút, bảo trì...)"),
  AUDIT_LOG_VIEW("Xem nhật ký hệ thống (audit logs)"),

  // CFO — financial governance
  DEPT_BUDGET_ALLOCATE("Cấp quota tổng cho phòng ban (System Fund → Dept)"),
  DASHBOARD_VIEW_GLOBAL("Xem dashboard tổng quan dòng tiền & dư nợ toàn công ty");

  private final String description;
}
