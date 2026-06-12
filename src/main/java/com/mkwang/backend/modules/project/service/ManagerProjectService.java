package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.project.dto.request.CreateManagerProjectRequest;
import com.mkwang.backend.modules.project.dto.request.UpdateManagerProjectRequest;
import com.mkwang.backend.modules.project.dto.response.AvailableMemberResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberDetailResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberSummaryResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectSummaryResponse;
import com.mkwang.backend.modules.project.entity.ProjectStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ManagerProjectService {

    PageResponse<DepartmentMemberSummaryResponse> getDepartmentMembers(
            Long managerId, String search, int page, int limit);

    DepartmentMemberDetailResponse getDepartmentMemberDetail(Long managerId, Long memberId);

    PageResponse<ProjectSummaryResponse> getDepartmentProjects(
            Long managerId, ProjectStatus status, String search, int page, int limit);

    ProjectDetailResponse getDepartmentProjectDetail(Long managerId, Long projectId);

    ProjectDetailResponse createProject(Long managerId, CreateManagerProjectRequest request);

    ProjectDetailResponse updateProject(Long managerId, Long projectId, UpdateManagerProjectRequest request);

    List<AvailableMemberResponse> getDepartmentTeamLeaders(Long managerId);

    /**
     * Returns the sum of totalBudget and availableBudget across all projects in the manager's dept.
     * Result: [totalProjectQuota, totalAvailableBalance]
     */
    BigDecimal[] getDeptBudgetSnapshot(Long managerId);

    /**
     * Returns count of projects per status for the manager's department.
     */
    Map<ProjectStatus, Long> getDeptProjectStatusCounts(Long managerId);
}

