package com.mkwang.backend.modules.accounting.dto.request;

import java.math.BigDecimal;

public record UpdatePayslipEntryRequest(
        BigDecimal baseSalary,
        BigDecimal bonus,
        BigDecimal allowance,
        BigDecimal deduction,
        BigDecimal advanceDeduct
) {}
