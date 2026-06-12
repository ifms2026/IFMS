package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;

public record DepartmentMemberSummaryResponse(
        Long id,
        String fullName,
        String email,
        String employeeCode,
        String avatar,
        String jobTitle,
        String status,
        int pendingRequestsCount,
        BigDecimal debtBalance
) {
}

