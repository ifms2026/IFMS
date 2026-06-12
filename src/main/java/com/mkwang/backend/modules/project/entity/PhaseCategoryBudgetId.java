package com.mkwang.backend.modules.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Composite primary key for PhaseCategoryBudget entity.
 * Combination of phase_id + category_id.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PhaseCategoryBudgetId implements Serializable {

    @Column(name = "phase_id")
    private Long phaseId;

    @Column(name = "category_id")
    private Long categoryId;
}

