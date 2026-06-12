package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectMemberRequest(

        @NotBlank(message = "position is required")
        @Size(max = 100, message = "position must be at most 100 characters")
        String position
) {}
