package com.mkwang.backend.modules.project.dto.response;

import java.util.List;

public record ProjectPhasesResponse(
        Long projectId,
        String projectName,
        List<ProjectPhaseOptionResponse> phases
) {
}

