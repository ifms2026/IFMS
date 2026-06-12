package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.modules.project.dto.request.CreateExpenseCategoryRequest;
import com.mkwang.backend.modules.project.dto.request.RemovePhaseCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.request.SetCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.response.CategoryBudgetItemResponse;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseCategoryBudgetResponse;
import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudget;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudgetId;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.project.entity.ProjectRole;
import com.mkwang.backend.modules.project.repository.ExpenseCategoryRepository;
import com.mkwang.backend.modules.project.repository.PhaseCategoryBudgetRepository;
import com.mkwang.backend.modules.project.repository.ProjectMemberRepository;
import com.mkwang.backend.modules.project.repository.ProjectPhaseRepository;
import com.mkwang.backend.modules.project.repository.ProjectRepository;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryBudgetServiceImpl implements CategoryBudgetService {

    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PhaseCategoryBudgetRepository phaseCategoryBudgetRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ProjectQueryService projectQueryService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('PROJECT_BUDGET_ALLOCATE','PROJECT_CATEGORY_MANAGE')")
    public PhaseCategoryBudgetResponse getPhaseCategoryBudgets(Long projectId, Long phaseId, User currentUser) {
        getProjectOrThrow(projectId);
        assertTlLeaderOf(currentUser.getId(), projectId);
        ProjectPhase phase = getPhaseOrThrow(phaseId);
        assertPhaseInProject(phase, projectId);

        List<CategoryBudgetItemResponse> items = phaseCategoryBudgetRepository.findByIdPhaseId(phaseId).stream()
                .map(this::toCategoryBudgetItem)
                .toList();

        return new PhaseCategoryBudgetResponse(projectId, phaseId, phase.getName(), items);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('PROJECT_BUDGET_ALLOCATE','PROJECT_CATEGORY_MANAGE')")
    public PhaseCategoryBudgetResponse updatePhaseCategoryBudgets(Long projectId, SetCategoryBudgetsRequest request, User currentUser) {
        getProjectOrThrow(projectId);
        assertTlLeaderOf(currentUser.getId(), projectId);
        ProjectPhase phase = getPhaseOrThrow(request.phaseId());
        assertPhaseInProject(phase, projectId);

        PhaseCategoryBudgetId budgetId = new PhaseCategoryBudgetId(request.phaseId(), request.categoryId());
        PhaseCategoryBudget targetBudget = phaseCategoryBudgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("PhaseCategoryBudget", "id", budgetId));

        if (request.budgetLimit().compareTo(targetBudget.getCurrentSpent()) < 0) {
            throw new BadRequestException(
                    "budgetLimit moi cua categoryId=" + request.categoryId() +
                            " khong duoc nho hon currentSpent=" + targetBudget.getCurrentSpent()
            );
        }

        List<PhaseCategoryBudget> phaseBudgets = phaseCategoryBudgetRepository.findByIdPhaseId(request.phaseId());
        BigDecimal currentTotal = phaseBudgets.stream()
                .map(PhaseCategoryBudget::getBudgetLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalTotal = currentTotal
                .subtract(targetBudget.getBudgetLimit())
                .add(request.budgetLimit());
        if (finalTotal.compareTo(phase.getBudgetLimit()) > 0) {
            throw new BadRequestException(
                    "Tong category budget sau cap nhat (" + finalTotal + ") vuot qua phase budget limit (" + phase.getBudgetLimit() + ")"
            );
        }

        targetBudget.setBudgetLimit(request.budgetLimit());
        phaseCategoryBudgetRepository.save(targetBudget);
        return buildPhaseCategoryBudgetResponse(projectId, phase);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('PROJECT_BUDGET_ALLOCATE','PROJECT_CATEGORY_MANAGE')")
    public PhaseCategoryBudgetResponse removePhaseCategoryBudgets(Long projectId, RemovePhaseCategoryBudgetsRequest request, User currentUser) {
        getProjectOrThrow(projectId);
        assertTlLeaderOf(currentUser.getId(), projectId);
        ProjectPhase phase = getPhaseOrThrow(request.phaseId());
        assertPhaseInProject(phase, projectId);

        PhaseCategoryBudgetId budgetId = new PhaseCategoryBudgetId(request.phaseId(), request.categoryId());
        PhaseCategoryBudget budgetToRemove = phaseCategoryBudgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("PhaseCategoryBudget", "id", budgetId));

        if (budgetToRemove.getCurrentSpent().compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException(
                    "Chi duoc remove categoryId=" + request.categoryId() + " khi currentSpent = 0"
            );
        }

        phaseCategoryBudgetRepository.delete(budgetToRemove);
        return buildPhaseCategoryBudgetResponse(projectId, phase);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_CATEGORY_MANAGE')")
    public List<ExpenseCategoryResponse> getAvailableCategories(Long projectId, User currentUser) {
        getProjectOrThrow(projectId);
        assertTlLeaderOf(currentUser.getId(), projectId);
        return projectQueryService.getAvailableCategoriesForProject(projectId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_CATEGORY_MANAGE')")
    public ExpenseCategoryResponse createProjectCategory(Long projectId, CreateExpenseCategoryRequest request, User currentUser) {
        Project project = getProjectOrThrow(projectId);
        assertTlLeaderOf(currentUser.getId(), projectId);
        ProjectPhase phase = getPhaseOrThrow(request.phaseId());
        assertPhaseInProject(phase, projectId);

        BigDecimal currentlyAllocated = phaseCategoryBudgetRepository.findByIdPhaseId(phase.getId()).stream()
                .map(PhaseCategoryBudget::getBudgetLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal afterCreateAllocated = currentlyAllocated.add(request.budgetLimit());
        if (afterCreateAllocated.compareTo(phase.getBudgetLimit()) > 0) {
            throw new BadRequestException(
                    "Tong category budget sau khi tao (" + afterCreateAllocated + ") vuot qua phase budget limit (" + phase.getBudgetLimit() + ")"
            );
        }

        ExpenseCategory category = ExpenseCategory.builder()
                .name(request.name().trim())
                .description(request.description())
                .isSystemDefault(false)
                .project(project)
                .createdAt(LocalDateTime.now())
                .createdBy(currentUser.getId())
                .build();

        ExpenseCategory saved = expenseCategoryRepository.save(category);

        PhaseCategoryBudget phaseCategoryBudget = PhaseCategoryBudget.builder()
                .id(new PhaseCategoryBudgetId(phase.getId(), saved.getId()))
                .phase(phase)
                .category(saved)
                .budgetLimit(request.budgetLimit())
                .currentSpent(BigDecimal.ZERO)
                .build();
        phaseCategoryBudgetRepository.save(phaseCategoryBudget);

        return new ExpenseCategoryResponse(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                saved.getIsSystemDefault(),
                projectId);
    }

    @Override
    @Transactional
    public void incrementSpent(Long phaseId, Long categoryId, BigDecimal amount) {
        PhaseCategoryBudgetId budgetId = new PhaseCategoryBudgetId(phaseId, categoryId);
        phaseCategoryBudgetRepository.findById(budgetId).ifPresent(budget -> {
            budget.setCurrentSpent(budget.getCurrentSpent().add(amount));
            phaseCategoryBudgetRepository.save(budget);
        });
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private ProjectPhase getPhaseOrThrow(Long phaseId) {
        return projectPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectPhase", "id", phaseId));
    }

    private void assertTlLeaderOf(Long userId, Long projectId) {
        if (!projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRole(projectId, userId, ProjectRole.LEADER)) {
            throw new UnauthorizedException("You are not Team Leader of thís project ");
        }
    }

    private void assertPhaseInProject(ProjectPhase phase, Long projectId) {
        if (!phase.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Phase khong thuoc project nay");
        }
    }

    private PhaseCategoryBudgetResponse buildPhaseCategoryBudgetResponse(Long projectId, ProjectPhase phase) {
        List<CategoryBudgetItemResponse> responseItems = phaseCategoryBudgetRepository.findByIdPhaseId(phase.getId()).stream()
                .map(this::toCategoryBudgetItem)
                .toList();
        return new PhaseCategoryBudgetResponse(projectId, phase.getId(), phase.getName(), responseItems);
    }

    private CategoryBudgetItemResponse toCategoryBudgetItem(PhaseCategoryBudget pcb) {
        return new CategoryBudgetItemResponse(
                pcb.getCategory().getId(),
                pcb.getCategory().getName(),
                pcb.getBudgetLimit(),
                pcb.getCurrentSpent(),
                pcb.getAvailableBalance()
        );
    }
}

