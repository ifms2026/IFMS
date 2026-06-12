package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.NotNull;

public record RemovePhaseCategoryBudgetsRequest(
        @NotNull Long phaseId,
        @NotNull Long categoryId
) {
}


