package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;

/**
 * Per-row result for the payroll import. importStatus = "ok" | "error".
 * Rows with importStatus = "error" have null id, payslipCode, userId, status.
 */
public record PayrollImportEntryResponse(
        Long id,
        String payslipCode,
        Long userId,
        String fullName,
        String employeeCode,
        BigDecimal baseSalary,
        BigDecimal bonus,
        BigDecimal allowance,
        BigDecimal deduction,
        BigDecimal advanceDeduct,
        BigDecimal finalNetSalary,
        String status,
        String importStatus,   // "ok" | "error"
        String importError     // null when ok
) {}
