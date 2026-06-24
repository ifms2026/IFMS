package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryListResponse;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryOptionResponse;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectOptionResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectPhaseOptionResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectPhasesResponse;
import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudget;
import com.mkwang.backend.modules.project.entity.PhaseStatus;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.repository.ExpenseCategoryRepository;
import com.mkwang.backend.modules.project.repository.PhaseCategoryBudgetRepository;
import com.mkwang.backend.modules.project.repository.ProjectMemberRepository;
import com.mkwang.backend.modules.project.repository.ProjectPhaseRepository;
import com.mkwang.backend.modules.project.repository.ProjectRepository;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectQueryServiceImpl implements ProjectQueryService {

    private static final Set<String> GLOBAL_PROJECT_VIEW_ROLES = Set.of("ADMIN", "ACCOUNTANT", "CFO");

    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PhaseCategoryBudgetRepository phaseCategoryBudgetRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOptionResponse> getProjects(User currentUser, ProjectStatus status) {
        String roleName = getRoleName(currentUser);
        List<Project> projects;

        if (GLOBAL_PROJECT_VIEW_ROLES.contains(roleName)) {
            projects = status == null
                    ? projectRepository.findAllByOrderByCreatedAtDesc()
                    : projectRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if ("MANAGER".equals(roleName)) {
            Long departmentId = getDepartmentId(currentUser);
            projects = status == null
                    ? projectRepository.findByDepartment_IdOrderByCreatedAtDesc(departmentId)
                    : projectRepository.findByDepartment_IdAndStatusOrderByCreatedAtDesc(departmentId, status);
        } else {
            projects = projectRepository.findMemberProjects(currentUser.getId(), status);
        }

        Map<Long, BigDecimal> spentByProject = loadProjectCategorySpent(
                projects.stream().map(Project::getId).toList()
        );

        return projects.stream()
                .map(project -> {
                    BigDecimal totalSpent = resolveTotalSpent(
                            project,
                            spentByProject.get(project.getId())
                    );
                    return new ProjectOptionResponse(
                            project.getId(),
                            project.getProjectCode(),
                            project.getName(),
                            project.getStatus().name(),
                            project.getDepartment().getId(),
                            project.getTotalBudget(),
                            resolveAvailableBudget(project, totalSpent),
                            totalSpent,
                            project.getCurrentPhase() != null ? project.getCurrentPhase().getId() : null,
                            project.getCurrentPhase() != null ? project.getCurrentPhase().getName() : null
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPhasesResponse getProjectPhases(User currentUser, Long projectId, PhaseStatus status) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        assertProjectAccess(currentUser, project);

        List<ProjectPhase> phases = status == null
                ? projectPhaseRepository.findByProject_IdOrderByCreatedAtAsc(projectId)
                : projectPhaseRepository.findByProject_IdAndStatusOrderByCreatedAtAsc(projectId, status);

        List<ProjectPhaseOptionResponse> phaseResponses = phases.stream()
                .map(phase -> new ProjectPhaseOptionResponse(
                        phase.getId(),
                        phase.getPhaseCode(),
                        phase.getName(),
                        phase.getBudgetLimit(),
                        phase.getCurrentSpent(),
                        phase.getStatus().name(),
                        phase.getStartDate(),
                        phase.getEndDate()
                ))
                .toList();

        return new ProjectPhasesResponse(project.getId(), project.getName(), phaseResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseCategoryListResponse getPhaseCategories(User currentUser, Long phaseId) {
        ProjectPhase phase = projectPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectPhase", "id", phaseId));
        assertProjectAccess(currentUser, phase.getProject());

        List<ExpenseCategoryOptionResponse> items = phaseCategoryBudgetRepository.findByIdPhaseId(phaseId).stream()
                .sorted(Comparator.comparing(budget -> budget.getCategory().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toCategoryOption)
                .toList();

        return new ExpenseCategoryListResponse(items);
    }

    @Override
    @Transactional(readOnly = true)
    public Project getProjectEntityById(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPhase getPhaseEntityById(Long phaseId) {
        return projectPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectPhase", "id", phaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseCategory getCategoryEntityById(Long categoryId) {
        return expenseCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", "id", categoryId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> getAvailableCategoriesForProject(Long projectId) {
        return expenseCategoryRepository.findAvailableForProject(projectId).stream()
                .map(cat -> new ExpenseCategoryResponse(
                        cat.getId(),
                        cat.getName(),
                        cat.getDescription(),
                        cat.getIsSystemDefault(),
                        cat.getProject() != null ? cat.getProject().getId() : null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getLeaderProjectIds(Long leaderId) {
        return projectMemberRepository.findProjectIdsByLeader(leaderId);
    }

    private ExpenseCategoryOptionResponse toCategoryOption(PhaseCategoryBudget budget) {
        return new ExpenseCategoryOptionResponse(
                budget.getCategory().getId(),
                budget.getCategory().getName()
        );
    }

    private Map<Long, BigDecimal> loadProjectCategorySpent(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> spentByProject = new HashMap<>();
        for (Object[] row : phaseCategoryBudgetRepository.sumCurrentSpentByProjectIds(projectIds)) {
            spentByProject.put(
                    ((Number) row[0]).longValue(),
                    coalesce((BigDecimal) row[1])
            );
        }
        return spentByProject;
    }

    private BigDecimal resolveTotalSpent(Project project, BigDecimal categorySpent) {
        return coalesce(project.getTotalSpent()).max(coalesce(categorySpent));
    }

    private BigDecimal resolveAvailableBudget(Project project, BigDecimal totalSpent) {
        BigDecimal fundedBudget = coalesce(project.getAvailableBudget())
                .add(coalesce(project.getTotalSpent()));
        return fundedBudget.subtract(coalesce(totalSpent)).max(BigDecimal.ZERO);
    }

    private BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void assertProjectAccess(User currentUser, Project project) {
        String roleName = getRoleName(currentUser);

        if (GLOBAL_PROJECT_VIEW_ROLES.contains(roleName)) {
            return;
        }
        if ("MANAGER".equals(roleName)) {
            Long departmentId = getDepartmentId(currentUser);
            if (!departmentId.equals(project.getDepartment().getId())) {
                throw new UnauthorizedException("Bạn không có quyền truy cập project này");
            }
            return;
        }

        if (!projectMemberRepository.existsByProject_IdAndUser_Id(project.getId(), currentUser.getId())) {
            throw new UnauthorizedException("Bạn không có quyền truy cập project này");
        }
    }

    private String getRoleName(User currentUser) {
        if (currentUser.getRole() == null || currentUser.getRole().getName() == null) {
            throw new BadRequestException("User role is missing");
        }
        return currentUser.getRole().getName().toUpperCase();
    }

    private Long getDepartmentId(User currentUser) {
        if (currentUser.getDepartment() == null || currentUser.getDepartment().getId() == null) {
            throw new BadRequestException("Manager must belong to a department");
        }
        return currentUser.getDepartment().getId();
    }
}




