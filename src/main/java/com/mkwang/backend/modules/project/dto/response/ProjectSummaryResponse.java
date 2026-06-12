package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectSummaryResponse(
        Long id,
        String projectCode,
        String name,
        String status,
        BigDecimal totalBudget,
        BigDecimal availableBudget,
        BigDecimal totalSpent,
        int memberCount,
        Long currentPhaseId,
        String currentPhaseName,
        LocalDateTime createdAt
) {}
