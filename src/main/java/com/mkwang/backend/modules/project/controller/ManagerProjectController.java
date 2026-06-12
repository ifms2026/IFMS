package com.mkwang.backend.modules.project.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.project.dto.request.CreateManagerProjectRequest;
import com.mkwang.backend.modules.project.dto.request.UpdateManagerProjectRequest;
import com.mkwang.backend.modules.project.dto.response.AvailableMemberResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberDetailResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberSummaryResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectSummaryResponse;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.service.ManagerProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/manager")
@RequiredArgsConstructor
@Tag(name = "Manager - Department & Projects", description = "Department member overview and project management for managers")
@SecurityRequirement(name = "bearerAuth")
public class ManagerProjectController {

    private final ManagerProjectService managerProjectService;

    @GetMapping("/department/members")
    @Operation(summary = "Get department members", description = "Returns a paginated list of users in the authenticated manager's department.")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentMemberSummaryResponse>>> getDepartmentMembers(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.success(managerProjectService.getDepartmentMembers(
                principal.getUser().getId(), search, page, limit
        )));
    }

    @GetMapping("/department/members/{id}")
    @Operation(summary = "Get member detail", description = "Returns detail of one department member and assigned projects.")
    public ResponseEntity<ApiResponse<DepartmentMemberDetailResponse>> getDepartmentMemberDetail(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success(
                managerProjectService.getDepartmentMemberDetail(principal.getUser().getId(), id)
        ));
    }

    @GetMapping("/projects")
    @Operation(summary = "Get department projects", description = "Returns a paginated list of projects in the authenticated manager's department.")
    public ResponseEntity<ApiResponse<PageResponse<ProjectSummaryResponse>>> getDepartmentProjects(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.success(managerProjectService.getDepartmentProjects(
                principal.getUser().getId(), status, search, page, limit
        )));
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "Get project detail", description = "Returns project detail with phases and members for a project in manager's department.")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getDepartmentProjectDetail(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success(
                managerProjectService.getDepartmentProjectDetail(principal.getUser().getId(), id)
        ));
    }

    @PostMapping("/projects")
    @Operation(summary = "Create project", description = "Creates a new project under the manager's department and assigns the specified Team Leader.")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> createProject(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @Valid @RequestBody CreateManagerProjectRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                managerProjectService.createProject(principal.getUser().getId(), request)
        ));
    }

    @PutMapping("/projects/{id}")
    @Operation(summary = "Update project", description = "Updates project basics, status transitions, total budget, and Team Leader assignment.")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> updateProject(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateManagerProjectRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                managerProjectService.updateProject(principal.getUser().getId(), id, request)
        ));
    }

    @GetMapping("/department/team-leaders")
    @Operation(summary = "Get team leaders", description = "Returns active TEAM_LEADER users in the authenticated manager's department.")
    public ResponseEntity<ApiResponse<List<AvailableMemberResponse>>> getDepartmentTeamLeaders(
            @AuthenticationPrincipal UserDetailsAdapter principal) {

        return ResponseEntity.ok(ApiResponse.success(
                managerProjectService.getDepartmentTeamLeaders(principal.getUser().getId())
        ));
    }
}

