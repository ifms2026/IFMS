package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;

public record ProjectPhaseOptionResponse(
        Long id,
        String phaseCode,
        String name,
        BigDecimal budgetLimit,
        BigDecimal currentSpent
) {
}

