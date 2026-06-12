package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DepartmentMemberDetailResponse(
        Long id,
        String fullName,
        String email,
        String employeeCode,
        String avatar,
        String jobTitle,
        String phoneNumber,
        String status,
        BigDecimal debtBalance,
        int pendingRequestsCount,
        List<DepartmentMemberProjectAssignmentResponse> assignedProjects
) {
}

