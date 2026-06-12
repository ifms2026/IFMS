package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SetCategoryBudgetsRequest(
        @NotNull Long phaseId,
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.00") BigDecimal budgetLimit
) {
}

