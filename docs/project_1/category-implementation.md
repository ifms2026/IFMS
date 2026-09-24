# Category Budget Implementation — §3.4

Endpoints covered (API_Spec.md §3.4):
- `GET  /team-leader/projects/{id}/categories?phaseId=`
- `PUT  /team-leader/projects/{id}/categories`
- `GET  /team-leader/expense-categories`

---

## Pre-conditions

| # | Check | Detail |
|---|-------|--------|
| 1 | Permission seeded | `CATEGORY_BUDGET_MANAGE` must exist in `permissions` table and be assigned to `TEAM_LEADER` role |
| 2 | No `categoryCode` | `ExpenseCategory` entity has no `categoryCode` column — do not include in any response DTO |
| 3 | `PhaseCategoryBudget.getAvailableBalance()` | Entity method already exists: returns `budgetLimit - currentSpent` |
| 4 | `ProjectRole.LEADER` | Enum value in `modules/project/entity/ProjectRole.java` — used to verify TL access |
| 5 | Lazy loading | `PhaseCategoryBudget.phase` and `.category` are LAZY — existing `findByIdPhaseId` loads them via deriving join; verify N+1 isn't introduced in batch operations |

---

## Step 1 — Request DTOs

**`modules/project/dto/request/CategoryBudgetItemRequest.java`**
```java
package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CategoryBudgetItemRequest(
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal budgetLimit
) {}
```

**`modules/project/dto/request/SetCategoryBudgetsRequest.java`**
```java
package com.mkwang.backend.modules.project.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SetCategoryBudgetsRequest(
        @NotNull Long phaseId,
        @NotEmpty @Valid List<CategoryBudgetItemRequest> categories
) {}
```

---

## Step 2 — Response DTOs

**`modules/project/dto/response/CategoryBudgetItemResponse.java`**
```java
package com.mkwang.backend.modules.project.dto.response;

import java.math.BigDecimal;

public record CategoryBudgetItemResponse(
        Long categoryId,
        String categoryName,
        BigDecimal budgetLimit,
        BigDecimal currentSpent,
        BigDecimal remaining
) {}
```

**`modules/project/dto/response/PhaseCategoryBudgetResponse.java`**
```java
package com.mkwang.backend.modules.project.dto.response;

import java.util.List;

public record PhaseCategoryBudgetResponse(
        Long projectId,
        Long phaseId,
        String phaseName,
        List<CategoryBudgetItemResponse> categories
) {}
```

**`modules/project/dto/response/ExpenseCategoryResponse.java`**
```java
package com.mkwang.backend.modules.project.dto.response;

public record ExpenseCategoryResponse(
        Long id,
        String name,
        String description,
        Boolean isSystemDefault
) {}
```

> Do NOT reuse `ExpenseCategoryListResponse` or `ExpenseCategoryOptionResponse` — those are dropdown-only responses (id + name) and lack `description` / `isSystemDefault`.

---

## Step 3 — Repository Additions

### `PhaseCategoryBudgetRepository`

Add one method. Use explicit `@Query` rather than derived delete to avoid ambiguity with `@EmbeddedId` path:

```java
// modules/project/repository/PhaseCategoryBudgetRepository.java

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Modifying
@Query("DELETE FROM PhaseCategoryBudget pcb WHERE pcb.id.phaseId = :phaseId")
void deleteByPhaseId(@Param("phaseId") Long phaseId);
```

### `ProjectMemberRepository`

Add one derived method to check LEADER role for a specific project:

```java
// modules/project/repository/ProjectMemberRepository.java

import com.mkwang.backend.modules.project.entity.ProjectRole;

boolean existsByProject_IdAndUser_IdAndProjectRole(Long projectId, Long userId, ProjectRole projectRole);
```

---

## Step 4 — Service Interface

**`modules/project/service/CategoryBudgetService.java`**
```java
package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.modules.project.dto.request.SetCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseCategoryBudgetResponse;
import com.mkwang.backend.modules.user.entity.User;

import java.util.List;

public interface CategoryBudgetService {

    PhaseCategoryBudgetResponse getPhaseCategoryBudgets(Long projectId, Long phaseId, User currentUser);

    PhaseCategoryBudgetResponse setPhaseCategoryBudgets(Long projectId, SetCategoryBudgetsRequest request, User currentUser);

    List<ExpenseCategoryResponse> getAllExpenseCategories();
}
```

---

## Step 5 — Service Implementation

**`modules/project/service/CategoryBudgetServiceImpl.java`**
```java
package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.modules.project.dto.request.CategoryBudgetItemRequest;
import com.mkwang.backend.modules.project.dto.request.SetCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.response.CategoryBudgetItemResponse;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseCategoryBudgetResponse;
import com.mkwang.backend.modules.project.entity.*;
import com.mkwang.backend.modules.project.repository.*;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryBudgetServiceImpl implements CategoryBudgetService {

    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PhaseCategoryBudgetRepository phaseCategoryBudgetRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATEGORY_BUDGET_MANAGE')")
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
    @PreAuthorize("hasAuthority('CATEGORY_BUDGET_MANAGE')")
    public PhaseCategoryBudgetResponse setPhaseCategoryBudgets(Long projectId, SetCategoryBudgetsRequest request, User currentUser) {
        getProjectOrThrow(projectId);
        assertTlLeaderOf(currentUser.getId(), projectId);
        ProjectPhase phase = getPhaseOrThrow(request.phaseId());
        assertPhaseInProject(phase, projectId);

        BigDecimal totalRequested = request.categories().stream()
                .map(CategoryBudgetItemRequest::budgetLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRequested.compareTo(phase.getBudgetLimit()) > 0) {
            throw new BadRequestException(
                "Tổng category budget (" + totalRequested + ") vượt quá phase budget limit (" + phase.getBudgetLimit() + ")"
            );
        }

        // Preserve currentSpent for categories that already have spending
        Map<Long, BigDecimal> existingSpent = phaseCategoryBudgetRepository.findByIdPhaseId(request.phaseId()).stream()
                .collect(Collectors.toMap(
                        pcb -> pcb.getId().getCategoryId(),
                        PhaseCategoryBudget::getCurrentSpent
                ));

        List<Long> categoryIds = request.categories().stream()
                .map(CategoryBudgetItemRequest::categoryId)
                .toList();
        List<ExpenseCategory> foundCategories = expenseCategoryRepository.findAllById(categoryIds);
        if (foundCategories.size() != categoryIds.size()) {
            throw new ResourceNotFoundException("One or more ExpenseCategory IDs not found");
        }
        Map<Long, ExpenseCategory> categoryMap = foundCategories.stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, c -> c));

        phaseCategoryBudgetRepository.deleteByPhaseId(request.phaseId());
        phaseCategoryBudgetRepository.flush();

        List<PhaseCategoryBudget> newBudgets = request.categories().stream()
                .map(item -> PhaseCategoryBudget.builder()
                        .id(new PhaseCategoryBudgetId(request.phaseId(), item.categoryId()))
                        .phase(phase)
                        .category(categoryMap.get(item.categoryId()))
                        .budgetLimit(item.budgetLimit())
                        .currentSpent(existingSpent.getOrDefault(item.categoryId(), BigDecimal.ZERO))
                        .build())
                .toList();
        phaseCategoryBudgetRepository.saveAll(newBudgets);

        List<CategoryBudgetItemResponse> responseItems = newBudgets.stream()
                .map(this::toCategoryBudgetItem)
                .toList();
        return new PhaseCategoryBudgetResponse(projectId, request.phaseId(), phase.getName(), responseItems);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATEGORY_BUDGET_MANAGE')")
    public List<ExpenseCategoryResponse> getAllExpenseCategories() {
        return expenseCategoryRepository.findAll().stream()
                .sorted(Comparator.comparing(ExpenseCategory::getName))
                .map(cat -> new ExpenseCategoryResponse(
                        cat.getId(),
                        cat.getName(),
                        cat.getDescription(),
                        cat.getIsSystemDefault()))
                .toList();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

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
            throw new UnauthorizedException("Bạn không phải Team Leader của project này");
        }
    }

    private void assertPhaseInProject(ProjectPhase phase, Long projectId) {
        if (!phase.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Phase không thuộc project này");
        }
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
```

**Key decisions in `setPhaseCategoryBudgets`:**

| Decision | Reason |
|----------|--------|
| `deleteByPhaseId` + `flush()` before `saveAll` | Flush forces the DELETE to hit DB before INSERT to avoid unique-key collision within the same transaction |
| Preserve `currentSpent` from existing entries | Spent amounts are owned by payout events, not by the TL's budget edit — resetting to 0 would corrupt spending history |
| `SUM(budgetLimit) ≤ phase.getBudgetLimit()` | Spec says total category allocation must not exceed the phase ceiling |
| `foundCategories.size() != categoryIds.size()` guard | Catches invalid/deleted category IDs before the delete fires |

---

## Step 6 — Controller

**`modules/project/controller/TeamLeaderCategoryController.java`**
```java
package com.mkwang.backend.modules.project.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.project.dto.request.SetCategoryBudgetsRequest;
import com.mkwang.backend.modules.project.dto.response.ExpenseCategoryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseCategoryBudgetResponse;
import com.mkwang.backend.modules.project.service.CategoryBudgetService;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/team-leader")
@RequiredArgsConstructor
public class TeamLeaderCategoryController {

    private final CategoryBudgetService categoryBudgetService;
    private final UserService userService;

    @GetMapping("/projects/{id}/categories")
    public ResponseEntity<ApiResponse<PhaseCategoryBudgetResponse>> getPhaseCategoryBudgets(
            @PathVariable Long id,
            @RequestParam Long phaseId) {
        User currentUser = userService.getCurrentUser();
        PhaseCategoryBudgetResponse result = categoryBudgetService.getPhaseCategoryBudgets(id, phaseId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/projects/{id}/categories")
    public ResponseEntity<ApiResponse<PhaseCategoryBudgetResponse>> setPhaseCategoryBudgets(
            @PathVariable Long id,
            @Valid @RequestBody SetCategoryBudgetsRequest request) {
        User currentUser = userService.getCurrentUser();
        PhaseCategoryBudgetResponse result = categoryBudgetService.setPhaseCategoryBudgets(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/expense-categories")
    public ResponseEntity<ApiResponse<List<ExpenseCategoryResponse>>> getAllExpenseCategories() {
        List<ExpenseCategoryResponse> result = categoryBudgetService.getAllExpenseCategories();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
```

> `@RequestMapping("/team-leader")` on this controller may conflict with `TeamLeaderApprovalController` if both declare the same prefix — check if Spring allows two controllers with the same `@RequestMapping` prefix. They do; paths are disambiguated by method-level `@GetMapping`/`@PutMapping`.

---

## Step 7 — `ExpenseCategory` getter check

`ExpenseCategory.isSystemDefault` — if the entity field is `Boolean isSystemDefault` (object type), Lombok generates `getIsSystemDefault()`. If it is `boolean isSystemDefault` (primitive), Lombok generates `isIsSystemDefault()` which is wrong. Verify in the entity and adjust the call in `CategoryBudgetServiceImpl.getAllExpenseCategories()` accordingly:

```java
// Entity field: Boolean isSystemDefault  →  cat.getIsSystemDefault()
// Entity field: boolean isSystemDefault  →  cat.isSystemDefault()
```

---

## Summary

| Action | File | Notes |
|--------|------|-------|
| **CREATE** | `dto/request/CategoryBudgetItemRequest.java` | Inner item of the PUT body |
| **CREATE** | `dto/request/SetCategoryBudgetsRequest.java` | PUT body: phaseId + categories list |
| **CREATE** | `dto/response/CategoryBudgetItemResponse.java` | Per-category row with remaining computed |
| **CREATE** | `dto/response/PhaseCategoryBudgetResponse.java` | Outer response wrapper for GET + PUT |
| **CREATE** | `dto/response/ExpenseCategoryResponse.java` | Full category record (id, name, desc, isSystemDefault) |
| **CREATE** | `service/CategoryBudgetService.java` | Interface |
| **CREATE** | `service/CategoryBudgetServiceImpl.java` | Implementation |
| **CREATE** | `controller/TeamLeaderCategoryController.java` | 3 endpoints under `/team-leader` |
| **MODIFY** | `repository/PhaseCategoryBudgetRepository.java` | Add `deleteByPhaseId` with `@Modifying @Query` |
| **MODIFY** | `repository/ProjectMemberRepository.java` | Add `existsByProject_IdAndUser_IdAndProjectRole` |

**No Flyway migration needed** — no schema changes.
