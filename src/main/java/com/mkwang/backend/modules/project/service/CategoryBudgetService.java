package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.modules.project.dto.request.CreateExpenseCategoryRequest;
import com.mkwang.backend.modules.project.dto.request.RemovePhaseCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.request.SetCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseCategoryBudgetResponse;
import com.mkwang.backend.modules.user.entity.User;

import java.math.BigDecimal;
import java.util.List;

public interface CategoryBudgetService {

    PhaseCategoryBudgetResponse getPhaseCategoryBudgets(Long projectId, Long phaseId, User currentUser);

    PhaseCategoryBudgetResponse updatePhaseCategoryBudgets(Long projectId, SetCategoryBudgetsRequest request, User currentUser);

    PhaseCategoryBudgetResponse removePhaseCategoryBudgets(Long projectId, RemovePhaseCategoryBudgetsRequest request, User currentUser);

    // Returns system-wide categories + project-specific categories for projectId, sorted by name.
    List<ExpenseCategoryResponse> getAvailableCategories(Long projectId, User currentUser);

    // Atomically creates project category and phase-category budget row using request.phaseId + request.budgetLimit.
    ExpenseCategoryResponse createProjectCategory(Long projectId, CreateExpenseCategoryRequest request, User currentUser);

    void incrementSpent(Long phaseId, Long categoryId, BigDecimal amount);
}

