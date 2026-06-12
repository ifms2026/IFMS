package com.mkwang.backend.modules.project.dto.response;

public record DepartmentMemberProjectAssignmentResponse(
        Long projectId,
        String projectCode,
        String projectName,
        String projectRole,
        String position
) {
}

