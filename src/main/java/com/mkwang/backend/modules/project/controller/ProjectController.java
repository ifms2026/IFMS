package com.mkwang.backend.modules.project.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryListResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectOptionResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectPhasesResponse;
import com.mkwang.backend.modules.project.entity.PhaseStatus;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.service.ProjectQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectQueryService projectQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<ProjectOptionResponse>>> getProjects(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @RequestParam(required = false) ProjectStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                projectQueryService.getProjects(userDetails.getUser(), status)
        ));
    }

    @GetMapping("/{id}/phases")
    public ResponseEntity<ApiResponse<ProjectPhasesResponse>> getProjectPhases(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @PathVariable("id") Long projectId,
            @RequestParam(required = false) PhaseStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                projectQueryService.getProjectPhases(userDetails.getUser(), projectId, status)
        ));
    }

    @GetMapping("/{phaseId}")
    public ResponseEntity<ApiResponse<ExpenseCategoryListResponse>> getProjectPhaseCategories(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @PathVariable Long phaseId) {
        return ResponseEntity.ok(ApiResponse.success(
                projectQueryService.getPhaseCategories(userDetails.getUser(), phaseId)
        ));
    }

}


