package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PhaseDetailResponse(
        Long id,
        String phaseCode,
        String name,
        BigDecimal budgetLimit,
        BigDecimal currentSpent,
        String status,
        LocalDate startDate,
        LocalDate endDate
) {}
