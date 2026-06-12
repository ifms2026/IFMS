package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.dto.PageResponse;
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
import com.mkwang.backend.modules.user.entity.User;

import java.util.List;

public interface TeamLeaderProjectService {

    PageResponse<ProjectSummaryResponse> getLeaderProjects(
            User currentUser, ProjectStatus status, String search, int page, int limit);

    ProjectDetailResponse getLeaderProjectDetail(User currentUser, Long projectId);

    ProjectMemberResponse addMember(User currentUser, Long projectId, AddProjectMemberRequest request);

    ProjectMemberResponse updateMember(User currentUser, Long projectId, Long userId, UpdateProjectMemberRequest request);

    void removeMember(User currentUser, Long projectId, Long userId);

    List<AvailableMemberResponse> getAvailableMembers(User currentUser, Long projectId, String search);

    ProjectPhaseResponse createPhase(User currentUser, Long projectId, CreatePhaseRequest request);

    ProjectPhaseResponse updatePhase(User currentUser, Long projectId, Long phaseId, UpdatePhaseRequest request);

    // ── 3.2 Team Members Overview ──────────────────────────────────────
    PageResponse<TeamMemberSummaryResponse> getTeamMembers(
            User currentUser, Long projectId, String search, int page, int limit);

    TeamMemberDetailResponse getTeamMemberDetail(User currentUser, Long userId);
}

