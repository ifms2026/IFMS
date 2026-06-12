package com.mkwang.backend.modules.wallet.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.common.exception.InsufficientWalletBalanceException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Wallet entity — Unified wallet for all financial actors (User, Department, Project, CompanyFund).
 *
 * Balance model (industry-standard 2-field approach):
 *   balance        = total gross balance
 *   lockedBalance  = portion reserved for pending/approved-but-not-yet-disbursed operations
 *   availableBalance (derived) = balance - lockedBalance  ← what can actually be spent
 *
 * Concurrency strategy:
 *   Primary   : Pessimistic Write Lock via findByOwnerTypeAndOwnerIdForUpdate() in Repository
 *   Safety net: @Version (Optimistic Locking) as fallback for edge cases
 */
@Entity
@Table(
    name = "wallets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_owner", columnNames = {"owner_type", "owner_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "owner_type", nullable = false, length = 20)
  private WalletOwnerType ownerType;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  /**
   * Gross balance — total money in the wallet including locked amounts.
   */
  @Column(name = "balance", precision = 19, scale = 2, nullable = false, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
  @Builder.Default
  private BigDecimal balance = BigDecimal.ZERO;

  /**
   * Portion of balance that is reserved and cannot be spent.
   * Increases when a request is approved (funds reserved).
   * Decreases when the reservation is settled or released.
   */
  @Column(name = "locked_balance", precision = 19, scale = 2, nullable = false, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
  @Builder.Default
  private BigDecimal lockedBalance = BigDecimal.ZERO;

  @Version
  @Column(name = "version")
  private Long version;

  // ── Read helpers ────────────────────────────────────────────────

  /**
   * Available balance = balance - lockedBalance.
   * This is the only amount that can be spent or transferred out.
   */
  public BigDecimal getAvailableBalance() {
    return balance.subtract(lockedBalance);
  }

  public boolean hasSufficientBalance(BigDecimal amount) {
    return getAvailableBalance().compareTo(amount) >= 0;
  }

  // ── Mutation methods ────────────────────────────────────────────

  /**
   * Credit: money enters this wallet (balance increases).
   */
  public void credit(BigDecimal amount) {
    this.balance = this.balance.add(amount);
  }

  /**
   * Debit: immediately remove money from available balance.
   * Use for direct payouts where no prior reservation was made.
   */
  public void debit(BigDecimal amount) {
    if (!hasSufficientBalance(amount)) {
      throw new InsufficientWalletBalanceException(amount, getAvailableBalance(), "debit");
    }
    this.balance = this.balance.subtract(amount);
  }

  /**
   * Reserve: lock a portion of available balance for a pending operation
   * (e.g. request approved, awaiting Accountant disbursement).
   * balance stays the same; lockedBalance increases.
   */
  public void lock(BigDecimal amount) {
    if (!hasSufficientBalance(amount)) {
      throw new InsufficientWalletBalanceException(amount, getAvailableBalance(), "lock");
    }
    this.lockedBalance = this.lockedBalance.add(amount);
  }

  /**
   * Release: free a previously locked amount without spending it
   * (e.g. request cancelled or rejected after approval).
   */
  public void unlock(BigDecimal amount) {
    this.lockedBalance = this.lockedBalance.subtract(amount);
    if (this.lockedBalance.compareTo(BigDecimal.ZERO) < 0) {
      this.lockedBalance = BigDecimal.ZERO;
    }
  }

  /**
   * Settle: finalise a locked reservation — deduct from balance and release the lock.
   * Use when a previously locked amount is actually disbursed.
   */
  public void settle(BigDecimal amount) {
    unlock(amount);
    this.balance = this.balance.subtract(amount);
  }
}
