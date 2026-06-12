package com.mkwang.backend.modules.accounting.entity;

/**
 * Enum representing the status of a payroll period.
 */
public enum PayrollStatus {
  DRAFT, // Đang nhập liệu
  PROCESSING, // Đang xử lý chi lương
  COMPLETED // Đã hoàn thành chi lương
}
