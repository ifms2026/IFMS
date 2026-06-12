package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TeamMemberSummaryResponse(
        Long id,
        String fullName,
        String email,
        String employeeCode,
        String avatar,
        String jobTitle,
        String status,
        BigDecimal debtBalance,
        int pendingRequestsCount,
        List<MemberProjectInfoResponse> projects
) {}
