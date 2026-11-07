package com.mkwang.backend.modules.request.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DisbursementBudgetContextResponse(
        String projectCode,
        String projectName,
        String phaseCode,
        String phaseName,
        String categoryName,
        BigDecimal approvedAmount,
        LocalDateTime calculatedAt,
        Scope project,
        Scope phase,
        Scope category
) {
    public record Scope(
            BigDecimal budgetLimit,
            BigDecimal recognizedExpense,
            BigDecimal reservedForOtherRequests,
            BigDecimal openAdvance,
            BigDecimal availableNow,
            BigDecimal availableAfterThisRequest
    ) {}
}
