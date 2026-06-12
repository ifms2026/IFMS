package com.mkwang.backend.modules.project.dto.response;

public record AvailableMemberResponse(
        Long id,
        String fullName,
        String employeeCode,
        String avatar,
        String email,
        String jobTitle
) {}
