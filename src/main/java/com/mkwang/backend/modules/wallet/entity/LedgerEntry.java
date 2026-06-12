package com.mkwang.backend.modules.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LedgerEntry — one accounting line in the double-entry ledger.
 *
 * Every Transaction produces exactly two entries:
 *   - One DEBIT  on the source wallet  (money leaves)
 *   - One CREDIT on the destination wallet (money arrives)
 *
 * This table is the single source of truth for:
 *   - Wallet transaction history  (query by wallet_id)
 *   - Balance reconstruction      (walk entries by created_at, use balance_after)
 *   - Reconciliation              (every DEBIT has a matching CREDIT via transaction_id)
 *
 * Immutability contract: entries are NEVER updated or deleted.
 * Corrections use a new REVERSAL transaction with opposite-direction entries.
 */
@Entity
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_wallet_created", columnList = "wallet_id, created_at"),
        @Index(name = "idx_ledger_transaction",    columnList = "transaction_id")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
  private Transaction transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
  private Wallet wallet;

  /**
   * DEBIT  = money left this wallet  (balance decreased)
   * CREDIT = money entered this wallet (balance increased)
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10, updatable = false)
  private TransactionDirection direction;

  /**
   * Always positive. Use direction to determine sign.
   */
  @Column(name = "amount", precision = 19, scale = 2, nullable = false, updatable = false)
  private BigDecimal amount;

  /**
   * Snapshot of wallet.balance immediately after this entry was applied.
   * Used for chronological balance reconstruction and statement generation.
   */
  @Column(name = "balance_after", precision = 19, scale = 2, nullable = false, updatable = false)
  private BigDecimal balanceAfter;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  // ── Factory helpers ──────────────────────────────────────────────

  public static LedgerEntry debit(Transaction tx, Wallet wallet, BigDecimal amount, BigDecimal balanceAfter) {
    return LedgerEntry.builder()
        .transaction(tx)
        .wallet(wallet)
        .direction(TransactionDirection.DEBIT)
        .amount(amount)
        .balanceAfter(balanceAfter)
        .build();
  }

  public static LedgerEntry credit(Transaction tx, Wallet wallet, BigDecimal amount, BigDecimal balanceAfter) {
    return LedgerEntry.builder()
        .transaction(tx)
        .wallet(wallet)
        .direction(TransactionDirection.CREDIT)
        .amount(amount)
        .balanceAfter(balanceAfter)
        .build();
  }
}
