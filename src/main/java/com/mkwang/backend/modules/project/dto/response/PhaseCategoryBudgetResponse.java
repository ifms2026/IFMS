package com.mkwang.backend.modules.project.dto.response;

import java.util.List;

public record PhaseCategoryBudgetResponse(
        Long projectId,
        Long phaseId,
        String phaseName,
        List<CategoryBudgetItemResponse> categories
) {
}

