package com.mkwang.backend.modules.project.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ProjectPhase entity - Represents a phase/stage of a project.
 * Budget is allocated per phase, and employees can only create requests within
 * phase budget.
 */
@Entity
@Table(name = "project_phases", indexes = {
    @Index(name = "idx_project_phases_phase_code", columnList = "phase_code")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPhase extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Human-readable phase identifier.
   * Format: PH-{NAME}-{SEQ} e.g. PH-UIUX-01
   * Auto-generated at application layer before persistence.
   */
  @Column(name = "phase_code", nullable = false, unique = true, length = 30)
  private String phaseCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @Column(nullable = false)
  private String name;

  @Column(name = "budget_limit", precision = 19, scale = 2)
  @Builder.Default
  private BigDecimal budgetLimit = BigDecimal.ZERO;

  @Column(name = "current_spent", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
  @Builder.Default
  private BigDecimal currentSpent = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private PhaseStatus status = PhaseStatus.ACTIVE;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  /**
   * Get remaining budget for this phase
   */
  public BigDecimal getRemainingBudget() {
    return budgetLimit.subtract(currentSpent);
  }

  /**
   * Check if phase has sufficient budget for a request amount
   */
  public boolean hasSufficientBudget(BigDecimal amount) {
    return getRemainingBudget().compareTo(amount) >= 0;
  }

  /**
   * Add spent amount to current spent
   */
  public void addSpent(BigDecimal amount) {
    this.currentSpent = this.currentSpent.add(amount);
  }

  /**
   * Check if phase is active and can accept requests
   */
  public boolean isRequestable() {
    return status == PhaseStatus.ACTIVE;
  }
}
