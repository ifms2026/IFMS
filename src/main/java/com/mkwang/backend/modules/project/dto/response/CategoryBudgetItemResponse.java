package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;

public record CategoryBudgetItemResponse(
        Long categoryId,
        String categoryName,
        BigDecimal budgetLimit,
        BigDecimal currentSpent,
        BigDecimal remaining
) {
}

