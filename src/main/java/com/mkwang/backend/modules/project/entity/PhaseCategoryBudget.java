package com.mkwang.backend.modules.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * PhaseCategoryBudget entity - Many-to-Many between ProjectPhase and ExpenseCategory.
 * Team Leader sets a budget ceiling for each Category within each Phase.
 *
 * Balance-as-Safety-Gate: Members cannot create requests exceeding
 * (budget_limit - current_spent) for the corresponding category.
 */
@Entity
@Table(name = "phase_category_budgets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhaseCategoryBudget {

    @EmbeddedId
    private PhaseCategoryBudgetId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("phaseId")
    @JoinColumn(name = "phase_id")
    private ProjectPhase phase;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("categoryId")
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    /**
     * Maximum budget allocated for this category in this phase.
     */
    @Column(name = "budget_limit", precision = 19, scale = 2, nullable = false)
    private BigDecimal budgetLimit;

    /**
     * Amount already spent in this category. Auto-updated when Accountant pays out.
     */
    @Column(name = "current_spent", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    @Builder.Default
    private BigDecimal currentSpent = BigDecimal.ZERO;

    // ======================== Business Logic ========================

    /**
     * Get remaining available budget for this category in this phase.
     */
    public BigDecimal getAvailableBalance() {
        return budgetLimit.subtract(currentSpent);
    }

    /**
     * Check if the requested amount can be accommodated.
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return getAvailableBalance().compareTo(amount) >= 0;
    }

    /**
     * Record spending against this category budget.
     */
    public void addSpent(BigDecimal amount) {
        this.currentSpent = this.currentSpent.add(amount);
    }
}

