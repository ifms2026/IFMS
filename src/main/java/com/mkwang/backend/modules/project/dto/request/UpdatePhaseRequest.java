package com.mkwang.backend.modules.project.dto.request;

import com.mkwang.backend.modules.project.entity.PhaseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePhaseRequest(

        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @DecimalMin(value = "0.01", message = "budgetLimit must be greater than 0")
        BigDecimal budgetLimit,

        LocalDate endDate,

        PhaseStatus status
) {}
