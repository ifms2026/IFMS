package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryListResponse;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectOptionResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectPhasesResponse;
import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import com.mkwang.backend.modules.project.entity.PhaseStatus;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.user.entity.User;

import java.util.List;

public interface ProjectQueryService {

    List<ProjectOptionResponse> getProjects(User currentUser, ProjectStatus status);

    ProjectPhasesResponse getProjectPhases(User currentUser, Long projectId, PhaseStatus status);

    ExpenseCategoryListResponse getPhaseCategories(User currentUser, Long phaseId);

    // Returns system-wide categories + categories belonging to the given project, sorted by name.
    List<ExpenseCategoryResponse> getAvailableCategoriesForProject(Long projectId);

    Project getProjectEntityById(Long projectId);

    ProjectPhase getPhaseEntityById(Long phaseId);

    ExpenseCategory getCategoryEntityById(Long categoryId);

    List<Long> getLeaderProjectIds(Long leaderId);
}


