package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CategoryBudgetItemRequest(
        @NotNull Long categoryId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetLimit
) {
}

