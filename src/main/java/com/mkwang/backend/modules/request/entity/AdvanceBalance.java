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
 * It is reduced by two mechanisms:
 *   1. REIMBURSE request approved (expense invoices submitted) → reimbursedAmount increases.
 *      No wallet movement — purely an accounting settlement.
 *   2. Cash returned or payroll deducted → returnedAmount increases.
 *      Wallet movement: DEBIT user, CREDIT project (ADVANCE_RETURN transaction).
 *
 * remaining = original - reimbursed - returned
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
   * Total returned as actual cash (manual return or payroll deduction).
   * Increases when ADVANCE_RETURN transaction is executed. Wallet is debited.
   */
  @Column(name = "returned_amount", precision = 19, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal returnedAmount = BigDecimal.ZERO;

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
   * Called when remaining cash is returned manually or deducted from payroll.
   * Reduces remaining debt — wallet DEBIT happens separately via ADVANCE_RETURN transaction.
   *
   * @param amount the amount being returned
   */
  public void returnCash(BigDecimal amount) {
    validateSettlementAmount(amount);
    this.returnedAmount  = this.returnedAmount.add(amount);
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
