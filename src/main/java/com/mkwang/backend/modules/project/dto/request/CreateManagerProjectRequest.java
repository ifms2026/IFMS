package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateManagerProjectRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        String description,

        @NotNull(message = "totalBudget is required")
        @DecimalMin(value = "0.01", message = "totalBudget must be greater than 0")
        BigDecimal totalBudget,

        @NotNull(message = "teamLeaderId is required")
        Long teamLeaderId
) {
}

