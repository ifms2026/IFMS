package com.mkwang.backend.modules.project.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.project.dto.request.AddProjectMemberRequest;
import com.mkwang.backend.modules.project.dto.request.CreatePhaseRequest;
import com.mkwang.backend.modules.project.dto.request.UpdatePhaseRequest;
import com.mkwang.backend.modules.project.dto.request.UpdateProjectMemberRequest;
import com.mkwang.backend.modules.project.dto.response.AvailableMemberResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectMemberResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectPhaseResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectSummaryResponse;
import com.mkwang.backend.modules.project.dto.response.TeamMemberDetailResponse;
import com.mkwang.backend.modules.project.dto.response.TeamMemberSummaryResponse;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.service.TeamLeaderProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/team-leader")
@RequiredArgsConstructor
@Tag(name = "Team Leader - Projects & Phases", description = "Team Leader project setup, member management, and phase management")
@SecurityRequirement(name = "bearerAuth")
public class TeamLeaderProjectController {

    private final TeamLeaderProjectService teamLeaderProjectService;

    // ─────────────────────────────────────────────────────────────────
    // 3.1 Project & Member Management
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/projects")
    @Operation(
            summary = "Get Team Leader's projects",
            description = "Returns a paginated list of projects where the current user is the LEADER. Supports filtering by status and search by name/code."
    )
    public ResponseEntity<ApiResponse<PageResponse<ProjectSummaryResponse>>> getLeaderProjects(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        PageResponse<ProjectSummaryResponse> result = teamLeaderProjectService.getLeaderProjects(
                principal.getUser(), status, search, page, limit);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/projects/{id}")
    @Operation(
            summary = "Get Team Leader's project detail",
            description = "Returns full detail of a project (including phases and members) where the current user is the LEADER."
    )
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getLeaderProjectDetail(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id) {

        ProjectDetailResponse result = teamLeaderProjectService.getLeaderProjectDetail(principal.getUser(), id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/projects/{id}/members")
    @Operation(
            summary = "Add member to project",
            description = "Adds a user from the same department to the project with role MEMBER. The current user must be LEADER of the project."
    )
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> addMember(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @Valid @RequestBody AddProjectMemberRequest request) {

        ProjectMemberResponse result = teamLeaderProjectService.addMember(principal.getUser(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PutMapping("/projects/{id}/members/{userId}")
    @Operation(
            summary = "Update member position",
            description = "Updates the display position/title of a project member. Only the position field can be changed — projectRole cannot be modified by Team Leader."
    )
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> updateMember(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProjectMemberRequest request) {

        ProjectMemberResponse result = teamLeaderProjectService.updateMember(principal.getUser(), id, userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/projects/{id}/members/{userId}")
    @Operation(
            summary = "Remove member from project",
            description = "Removes a member from the project. Cannot remove the Team Leader (self) or a member with pending requests."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> removeMember(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @PathVariable Long userId) {

        teamLeaderProjectService.removeMember(principal.getUser(), id, userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Member removed from project successfully")));
    }

    @GetMapping("/projects/{id}/available-members")
    @Operation(
            summary = "Get available members for project",
            description = "Returns users in the same department who are not yet project members. Used to populate the member-add dropdown."
    )
    public ResponseEntity<ApiResponse<List<AvailableMemberResponse>>> getAvailableMembers(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @RequestParam(required = false) String search) {

        List<AvailableMemberResponse> result = teamLeaderProjectService.getAvailableMembers(
                principal.getUser(), id, search);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────────────────────────
    // Phase Management
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/projects/{id}/phases")
    @Operation(
            summary = "Create a new phase",
            description = "Adds a new phase to the project. Backend auto-generates phaseCode. Validates that total phase budgets do not exceed the project's availableBudget."
    )
    public ResponseEntity<ApiResponse<ProjectPhaseResponse>> createPhase(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @Valid @RequestBody CreatePhaseRequest request) {

        ProjectPhaseResponse result = teamLeaderProjectService.createPhase(principal.getUser(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PutMapping("/projects/{id}/phases/{phaseId}")
    @Operation(
            summary = "Update phase",
            description = "Updates a phase's name, budgetLimit, endDate, or status (ACTIVE/CLOSED). budgetLimit can only be decreased if still >= currentSpent, and can only be increased if the project has sufficient availableBudget."
    )
    public ResponseEntity<ApiResponse<ProjectPhaseResponse>> updatePhase(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long id,
            @PathVariable Long phaseId,
            @Valid @RequestBody UpdatePhaseRequest request) {

        ProjectPhaseResponse result = teamLeaderProjectService.updatePhase(
                principal.getUser(), id, phaseId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────────────────────────
    // 3.2 Team Members Overview
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/team-members")
    @Operation(
            summary = "List all team members (deduplicated)",
            description = "Returns a paginated list of all members across all projects where the current user is LEADER. " +
                    "Deduplicates by userId so members appearing in multiple projects appear once with a projects[] array. " +
                    "Optionally filter by projectId to show only members of a specific project. " +
                    "Each item includes debtBalance (wallet lockedBalance) and pendingRequestsCount."
    )
    public ResponseEntity<ApiResponse<PageResponse<TeamMemberSummaryResponse>>> getTeamMembers(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        PageResponse<TeamMemberSummaryResponse> result = teamLeaderProjectService.getTeamMembers(
                principal.getUser(), projectId, search, page, limit);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/team-members/{userId}")
    @Operation(
            summary = "Get team member detail",
            description = "Returns full detail for a single team member: profile, projects in TL scope, debtBalance, pendingRequestsCount, " +
                    "and the 10 most recent requests submitted to any of the Team Leader's projects."
    )
    public ResponseEntity<ApiResponse<TeamMemberDetailResponse>> getTeamMemberDetail(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long userId) {

        TeamMemberDetailResponse result = teamLeaderProjectService.getTeamMemberDetail(
                principal.getUser(), userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
