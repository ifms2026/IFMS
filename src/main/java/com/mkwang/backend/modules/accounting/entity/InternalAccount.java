package com.mkwang.backend.modules.accounting.entity;

import com.mkwang.backend.common.exception.BadRequestException;

/** IFMS management accounts; this registry is not a statutory chart of accounts. */
public enum InternalAccount {
    ADVANCE_RECEIVABLE("Tạm ứng nhân viên chưa quyết toán", true),
    PROJECT_EXPENSE("Chi phí dự án đã xác nhận", true),
    EMPLOYEE_REIMBURSEMENT_PAYABLE("Khoản phải hoàn cho nhân viên", false),
    PAYROLL_EXPENSE("Chi phí lương", true),
    PAYROLL_DEDUCTION_CLEARING("Khoản khấu trừ bảng lương", false),
    COMPANY_FUND_WALLET("Tiền quỹ công ty", true),
    EXTERNAL_BANK_CASH("Nguồn tiền ngân hàng bên ngoài", true),
    DEPARTMENT_WALLET("Tiền ví phòng ban", true),
    PROJECT_WALLET("Tiền ví dự án", true),
    OPENING_BALANCE_CLEARING("Đối ứng mở sổ có chứng từ", false);

    private final String label;
    private final boolean debitNormal;
    InternalAccount(String label, boolean debitNormal) { this.label = label; this.debitNormal = debitNormal; }
    public String label() { return label; }
    public boolean debitNormal() { return debitNormal; }
    public static InternalAccount require(String code) {
        try { return valueOf(code); }
        catch (IllegalArgumentException error) { throw new BadRequestException("Tài khoản nội bộ không hợp lệ"); }
    }
}
