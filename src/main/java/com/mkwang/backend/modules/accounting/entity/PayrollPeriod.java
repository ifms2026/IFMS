package com.mkwang.backend.modules.accounting.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PayrollPeriod entity - Represents a payroll period (monthly).
 * Contains all payslips for employees in this period.
 */
@Entity
@Table(name = "payroll_periods", indexes = {
    @Index(name = "idx_payroll_periods_period_code", columnList = "period_code")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPeriod extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Human-readable payroll period code.
   * Format: PR-{YEAR}-{MM} e.g. PR-2026-02
   * Auto-generated at application layer before persistence.
   */
  @Column(name = "period_code", nullable = false, unique = true, length = 30)
  private String periodCode;

  @Column(nullable = false)
  private String name; // e.g., "Lương T10/2025"

  @Column(nullable = false)
  private Integer month;

  @Column(nullable = false)
  private Integer year;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private PayrollStatus status = PayrollStatus.DRAFT;

  @Column(name = "netting_applied", nullable = false)
  @Builder.Default
  private boolean nettingApplied = false;

  @OneToMany(mappedBy = "period", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Payslip> payslips = new ArrayList<>();

  /**
   * Check if payroll period can be modified
   */
  public boolean isEditable() {
    return status == PayrollStatus.DRAFT;
  }

  /**
   * Check if payroll period is ready for execution
   */
  public boolean isExecutable() {
    return status == PayrollStatus.DRAFT && !payslips.isEmpty();
  }
}
