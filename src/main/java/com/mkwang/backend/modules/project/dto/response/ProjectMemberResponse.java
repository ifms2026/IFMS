package com.mkwang.backend.modules.project.dto.response;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long userId,
        String fullName,
        String avatar,
        String employeeCode,
        String projectRole,
        String position,
        LocalDateTime joinedAt
) {}
