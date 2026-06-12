package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;

public record AutoNettingSummaryEntryResponse(
        Long userId,
        String employeeCode,
        String fullName,
        BigDecimal outstandingDebt,
        BigDecimal deductedAmount,
        BigDecimal remainingDebt,
        String note
) {}
