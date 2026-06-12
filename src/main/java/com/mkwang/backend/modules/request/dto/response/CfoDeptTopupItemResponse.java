package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CfoDeptTopupItemResponse(
        Long id,
        String requestCode,
        String departmentName,
        BigDecimal amount,
        RequestStatus status,
        LocalDateTime createdAt
) {
}
