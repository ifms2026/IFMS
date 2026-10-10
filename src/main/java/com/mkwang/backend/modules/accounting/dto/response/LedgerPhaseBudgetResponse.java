package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record LedgerPhaseBudgetResponse(
        Long phaseId,
        String phaseName,
        BigDecimal budgetLimit,
        BigDecimal recognizedExpense,
        BigDecimal lockedForRequests,
        BigDecimal openAdvance,
        List<LedgerCategoryBudgetResponse> categories
) {}
