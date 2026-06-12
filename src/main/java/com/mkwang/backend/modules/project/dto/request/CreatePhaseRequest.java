package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePhaseRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "budgetLimit is required")
        @DecimalMin(value = "0.01", message = "budgetLimit must be greater than 0")
        BigDecimal budgetLimit,

        LocalDate startDate,

        LocalDate endDate
) {}
