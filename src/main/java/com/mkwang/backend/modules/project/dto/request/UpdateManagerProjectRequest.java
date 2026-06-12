package com.mkwang.backend.modules.project.dto.request;

import com.mkwang.backend.modules.project.entity.ProjectStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateManagerProjectRequest(
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        String description,

        @DecimalMin(value = "0.01", message = "totalBudget must be greater than 0")
        BigDecimal totalBudget,

        ProjectStatus status,

        Long teamLeaderId
) {
}

