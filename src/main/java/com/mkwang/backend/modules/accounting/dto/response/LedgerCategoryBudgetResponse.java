package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;

public record LedgerCategoryBudgetResponse(
        Long categoryId,
        String categoryName,
        BigDecimal budgetLimit,
        BigDecimal recognizedExpense,
        BigDecimal lockedForRequests,
        BigDecimal openAdvance
) {}
