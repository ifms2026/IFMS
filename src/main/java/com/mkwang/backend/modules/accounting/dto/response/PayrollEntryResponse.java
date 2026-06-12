package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.PayslipStatus;

import java.math.BigDecimal;

public record PayrollEntryResponse(
        Long id,
        String payslipCode,
        Long userId,
        String fullName,
        String avatar,
        String employeeCode,
        String jobTitle,
        BigDecimal baseSalary,
        BigDecimal bonus,
        BigDecimal allowance,
        BigDecimal deduction,
        BigDecimal advanceDeduct,
        BigDecimal finalNetSalary,
        PayslipStatus status
) {
}

