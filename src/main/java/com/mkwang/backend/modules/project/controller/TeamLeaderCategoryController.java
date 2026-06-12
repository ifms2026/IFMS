package com.mkwang.backend.modules.project.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.project.dto.request.CreateExpenseCategoryRequest;
import com.mkwang.backend.modules.project.dto.request.RemovePhaseCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.request.SetCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseCategoryBudgetResponse;
import com.mkwang.backend.modules.project.service.CategoryBudgetService;
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

@RestController
@RequestMapping("/team-leader")
@RequiredArgsConstructor
@Tag(name = "Team Leader - Categories & Budgets", description = "Manage project expense categories and phase budget allocations")
@SecurityRequirement(name = "bearerAuth")
public class TeamLeaderCategoryController {

    private final CategoryBudgetService categoryBudgetService;

    @GetMapping("/projects/{id}/categories")
    @Operation(
        summary = "Get phase-category budget matrix",
        description = "Returns the budget allocation table for a given phase inside a project. Each row shows a category with its budget_limit and current_spent. Team Leader must be the LEADER of this project."
    )
    public ResponseEntity<ApiResponse<PhaseCategoryBudgetResponse>> getPhaseCategoryBudgets(
            @PathVariable Long id,
            @RequestParam Long phaseId,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        PhaseCategoryBudgetResponse result = categoryBudgetService.getPhaseCategoryBudgets(id, phaseId, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/projects/{id}/categories")
    @Operation(
        summary = "Update one phase-category budget",
        description = "Updates budget_limit for exactly one category in a phase. Throws if updated phase total exceeds phase budgetLimit or if new budget_limit is smaller than current_spent."
    )
    public ResponseEntity<ApiResponse<PhaseCategoryBudgetResponse>> updatePhaseCategoryBudgets(
            @PathVariable Long id,
            @Valid @RequestBody SetCategoryBudgetsRequest request,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        PhaseCategoryBudgetResponse result = categoryBudgetService.updatePhaseCategoryBudgets(id, request, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/projects/{id}/categories")
    @Operation(
        summary = "Remove one phase-category budget",
        description = "Removes exactly one category budget from a phase. Removal is allowed only when current_spent == 0."
    )
    public ResponseEntity<ApiResponse<PhaseCategoryBudgetResponse>> removePhaseCategoryBudgets(
            @PathVariable Long id,
            @Valid @RequestBody RemovePhaseCategoryBudgetsRequest request,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        PhaseCategoryBudgetResponse result = categoryBudgetService.removePhaseCategoryBudgets(id, request, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/expense-categories")
    @Operation(
        summary = "List available expense categories for a project",
        description = "Returns all system-wide categories (isSystemDefault=true) plus any project-specific categories created by this Team Leader for the given project. Used to populate the category picker when setting phase budgets."
    )
    public ResponseEntity<ApiResponse<List<ExpenseCategoryResponse>>> getAvailableCategories(
            @RequestParam Long projectId,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        List<ExpenseCategoryResponse> result = categoryBudgetService.getAvailableCategories(projectId, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/projects/{id}/expense-categories")
    @Operation(
        summary = "Create a project-specific expense category",
        description = "Creates a custom expense category scoped to this project (isSystemDefault=false) and immediately creates phase_category_budget for the provided phaseId + budgetLimit in the same transaction."
    )
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> createProjectCategory(
            @PathVariable Long id,
            @Valid @RequestBody CreateExpenseCategoryRequest request,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        ExpenseCategoryResponse result = categoryBudgetService.createProjectCategory(id, request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }
}

