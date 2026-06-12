package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemberRecentRequestResponse(
        Long id,
        String requestCode,
        String type,
        BigDecimal amount,
        String status,
        String projectCode,
        String categoryName,
        LocalDateTime createdAt
) {}
