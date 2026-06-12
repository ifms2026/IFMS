package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.PayrollStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PayrollPeriodDetailResponse(
        Long id,
        String periodCode,
        String name,
        Integer month,
        Integer year,
        LocalDate startDate,
        LocalDate endDate,
        PayrollStatus status,
        int employeeCount,
        BigDecimal totalNetPayroll,
        List<PayrollEntryResponse> entries,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

