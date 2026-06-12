package com.mkwang.backend.modules.project.dto.response;

import java.time.LocalDateTime;

/**
 * Project info inside a team member's profile — used by both list and detail responses.
 * The detail variant includes joinedAt; the list variant omits it (null).
 */
public record MemberProjectInfoResponse(
        Long projectId,
        String projectCode,
        String projectName,
        String position,
        LocalDateTime joinedAt   // null in list, populated in detail
) {}
