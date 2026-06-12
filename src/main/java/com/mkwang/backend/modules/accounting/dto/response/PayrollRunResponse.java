package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.PayrollStatus;

import java.math.BigDecimal;

public record PayrollRunResponse(
        Long periodId,
        String periodCode,
        PayrollStatus status,
        int payslipsGenerated,
        BigDecimal totalNetPayroll
) {}
