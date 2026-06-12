package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateExpenseCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull Long phaseId,
        @NotNull @DecimalMin("0.00") BigDecimal budgetLimit
) {}
