package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;

public record ProjectOptionResponse(
        Long id,
        String projectCode,
        String name,
        String status,
        Long departmentId,
        BigDecimal totalBudget,
        BigDecimal availableBudget,
        BigDecimal totalSpent,
        Long currentPhaseId,
        String currentPhaseName
) {
}

