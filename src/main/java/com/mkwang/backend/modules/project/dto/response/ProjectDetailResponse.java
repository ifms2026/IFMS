package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectDetailResponse(
        Long id,
        String projectCode,
        String name,
        String description,
        String status,
        BigDecimal totalBudget,
        BigDecimal availableBudget,
        BigDecimal totalSpent,
        Long departmentId,
        Long managerId,
        Long currentPhaseId,
        List<PhaseDetailResponse> phases,
        List<ProjectMemberResponse> members,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
