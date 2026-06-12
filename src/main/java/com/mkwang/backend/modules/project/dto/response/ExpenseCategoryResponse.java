package com.mkwang.backend.modules.project.dto.response;

public record ExpenseCategoryResponse(
        Long id,
        String name,
        String description,
        Boolean isSystemDefault,
        Long projectId   // null = system-wide; non-null = project-specific
) {
}

