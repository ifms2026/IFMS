package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AdvanceEmployeeSummaryResponse(
        Long employeeId,
        String employeeName,
        String departmentName,
        int openAdvanceCount,
        BigDecimal totalDisbursed,
        BigDecimal totalRemaining,
        List<AdvanceBalanceDetailResponse> advances
) {}
