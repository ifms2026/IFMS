package com.mkwang.backend.modules.request.entity;

import com.mkwang.backend.common.exception.AdvanceBalanceAlreadySettledException;
import com.mkwang.backend.common.exception.InvalidSettlementAmountException;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AdvanceBalance — tracks the outstanding debt from an ADVANCE request.
 *
 * One record is created per approved ADVANCE payout.
 * It is settled by approved expense receipts, actual cash returned by the employee,
 * or a payroll offset. These amounts remain separate for audit and reconciliation.
 *
 * remaining = original - reimbursed - cash returned - payroll offset - legacy unclassified
 * When remaining = 0 → status = SETTLED.
 *
 * Audit trail:
 *   - Reimbursement history : query requests WHERE advance_balance_id = this.id AND type = REIMBURSE
 *   - Cash return history   : query transactions WHERE reference_type = ADVANCE_BALANCE AND reference_id = this.id
 */
@Entity
@Table(
    name = "advance_balances",
    indexes = {
        @Index(name = "idx_advance_balance_user_status", columnList = "user_id, status")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceBalance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  /**
   * The original ADVANCE request that created this debt.
   * Unique: one advance request produces exactly one balance record.
   */
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "advance_request_id", nullable = false, unique = true, updatable = false)
  private Request advanceRequest;

  /**
   * Amount originally disbursed (= advance request approvedAmount).
   * Never changes after creation.
   */
  @Column(name = "original_amount", precision = 19, scale = 2, nullable = false, updatable = false)
  private BigDecimal originalAmount;

  /**
   * Total settled via approved REIMBURSE invoices.
   * Increases on each REIMBURSE PAID. No wallet movement.
   */
  @Column(name = "reimbursed_amount", precision = 19, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal reimbursedAmount = BigDecimal.ZERO;

  /**
   * Actual money returned by the employee through an ADVANCE_RETURN wallet transaction.
   */
  @Column(name = "cash_returned_amount", precision = 19, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal cashReturnedAmount = BigDecimal.ZERO;

  /** Amount offset against payroll; no wallet transfer is created for this amount. */
  @Column(name = "payroll_offset_amount", precision = 19, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal payrollOffsetAmount = BigDecimal.ZERO;

  /** Historical settlement value whose source (cash return or payroll) cannot be reconstructed. */
  @Column(name = "legacy_unclassified_amount", precision = 19, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal legacyUnclassifiedAmount = BigDecimal.ZERO;

  /**
   * Remaining debt = original - reimbursed - returned.
   * Maintained as a stored column (not derived) for query performance and locking.
   */
  @Column(name = "remaining_amount", precision = 19, scale = 2, nullable = false)
  private BigDecimal remainingAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private AdvanceBalanceStatus status = AdvanceBalanceStatus.OUTSTANDING;

  /**
   * Set when remaining_amount reaches 0.
   */
  @Column(name = "settled_at")
  private LocalDateTime settledAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * REIMBURSE requests linked to this advance (for audit trail navigation).
   */
  @OneToMany(mappedBy = "advanceBalance", fetch = FetchType.LAZY)
  @Builder.Default
  private List<Request> reimburseRequests = new ArrayList<>();

  // ── Business logic ───────────────────────────────────────────────

  /**
   * Called when a REIMBURSE request is PAID.
   * Reduces remaining debt by the reimbursed amount — no wallet movement.
   *
   * @param amount the approvedAmount from the REIMBURSE request
   */
  public void reimburse(BigDecimal amount) {
    validateSettlementAmount(amount);
    this.reimbursedAmount = this.reimbursedAmount.add(amount);
    this.remainingAmount  = this.remainingAmount.subtract(amount);
    updateStatus();
  }

  /**
   * Called atomically with an actual USER → PROJECT ADVANCE_RETURN wallet transfer.
   *
   * @param amount the amount being returned
   */
  public void returnCash(BigDecimal amount) {
    validateSettlementAmount(amount);
    this.cashReturnedAmount = this.cashReturnedAmount.add(amount);
    this.remainingAmount = this.remainingAmount.subtract(amount);
    updateStatus();
  }

  /** Apply a payroll offset. This is a balance settlement, not a cash return. */
  public void applyPayrollOffset(BigDecimal amount) {
    validateSettlementAmount(amount);
    this.payrollOffsetAmount = this.payrollOffsetAmount.add(amount);
    this.remainingAmount = this.remainingAmount.subtract(amount);
    updateStatus();
  }

  public boolean isSettled() {
    return this.status == AdvanceBalanceStatus.SETTLED;
  }

  public boolean hasOutstanding() {
    return this.remainingAmount.compareTo(BigDecimal.ZERO) > 0;
  }

  // ── Private helpers ──────────────────────────────────────────────

  private void validateSettlementAmount(BigDecimal amount) {
    if (isSettled()) {
      throw new AdvanceBalanceAlreadySettledException(this.id);
    }
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidSettlementAmountException("Settlement amount must be positive");
    }
    if (amount.compareTo(this.remainingAmount) > 0) {
      throw new InvalidSettlementAmountException(
          "Settlement amount " + amount + " exceeds remaining balance " + this.remainingAmount);
    }
  }

  private void updateStatus() {
    if (this.remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
      this.status     = AdvanceBalanceStatus.SETTLED;
      this.settledAt  = LocalDateTime.now();
    } else {
      this.status = AdvanceBalanceStatus.PARTIALLY_SETTLED;
    }
  }
}
