package com.mkwang.backend.modules.project.dto.response;

import com.mkwang.backend.modules.project.entity.ProjectStatus;

import java.math.BigDecimal;
import java.util.List;

public record ProjectPhasesResponse(
        Long projectId,
        String projectName,
        ProjectStatus status,
        BigDecimal totalBudget,
        BigDecimal totalSpent,
        BigDecimal availableBudget,
        Long currentPhaseId,
        String currentPhaseName,
        List<ProjectPhaseOptionResponse> phases
) {
}

