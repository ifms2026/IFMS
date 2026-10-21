package com.mkwang.backend.modules.accounting.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payslip entity - Individual employee payslip for a payroll period.
 * Contains salary breakdown and auto-netting calculation.
 */
@Entity
@Table(name = "payslips", indexes = {
    @Index(name = "idx_payslips_payslip_code", columnList = "payslip_code")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payslip extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name="replaces_payslip_id")
  private Long replacesPayslipId;

  /**
   * Human-readable individual payslip code. Used as document reference when exporting PDF.
   * Format: PSL-{EMP_CODE}-{MMYY} e.g. PSL-EMP001-0226
   * Auto-generated at application layer before persistence.
   */
  @Column(name = "payslip_code", nullable = false, unique = true, length = 30)
  private String payslipCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "period_id", nullable = false)
  private PayrollPeriod period;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "base_salary", precision = 19, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal baseSalary = BigDecimal.ZERO;

  @Column(name = "bonus", precision = 19, scale = 2)
  @Builder.Default
  private BigDecimal bonus = BigDecimal.ZERO;

  @Column(name = "allowance", precision = 19, scale = 2)
  @Builder.Default
  private BigDecimal allowance = BigDecimal.ZERO;

  @Column(name = "deduction", precision = 19, scale = 2)
  @Builder.Default
  private BigDecimal deduction = BigDecimal.ZERO;

  @Column(name = "advance_deduct", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
  @Builder.Default
  private BigDecimal advanceDeduct = BigDecimal.ZERO; // Snapshot of debt deducted

  @Column(name = "final_net_salary", precision = 19, scale = 2)
  @Builder.Default
  private BigDecimal finalNetSalary = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private PayslipStatus status = PayslipStatus.DRAFT;

  @Column(name = "payment_date")
  private LocalDateTime paymentDate;

  /**
   * Calculate gross salary (before debt deduction)
   */
  public BigDecimal getGrossSalary() {
    return baseSalary.add(bonus).add(allowance).subtract(deduction);
  }

  /**
   * Calculate and set final net salary with auto-netting
   * 
   * @param currentDebt The current debt balance from wallet
   * @return The remaining debt after deduction
   */
  public BigDecimal calculateNetSalary(BigDecimal currentDebt) {
    BigDecimal gross = getGrossSalary();

    this.advanceDeduct = currentDebt.min(gross.multiply(new BigDecimal("0.5")).setScale(2, java.math.RoundingMode.HALF_UP));
    this.finalNetSalary = gross.subtract(advanceDeduct);
    return currentDebt.subtract(advanceDeduct);
  }
}
