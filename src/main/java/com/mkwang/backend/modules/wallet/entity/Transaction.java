package com.mkwang.backend.modules.wallet.entity;

import com.mkwang.backend.common.exception.InvalidTransactionStatusTransitionException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction — business event record (one per financial operation).
 *
 * Responsibilities:
 *   - Identifies WHAT happened (type, amount, reference, description)
 *   - Links to the external payment gateway when applicable
 *   - Tracks lifecycle status (PENDING → SUCCESS / FAILED / CANCELLED)
 *
 * NOT responsible for:
 *   - Which wallet was debited / credited  →  LedgerEntry
 *   - Balance snapshots                    →  LedgerEntry
 *   - Double-entry pairing                 →  LedgerEntry
 *
 * Immutability contract:
 *   Once status = SUCCESS, this record must never be updated or deleted.
 *   Corrections are made via a new REVERSAL transaction + matching entries.
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_txn_code",      columnList = "transaction_code"),
        @Index(name = "idx_txn_reference", columnList = "reference_type, reference_id"),
        @Index(name = "idx_txn_created",   columnList = "created_at")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Human-readable code for audit and statement lookup.
   * Format: TXN-{RANDOM_HEX8}  e.g. TXN-8829145A
   * Generated at application layer before persistence.
   */
  @Column(name = "transaction_code", nullable = false, unique = true, length = 30)
  private String transactionCode;

  /**
   * Total amount of this transaction. Always positive.
   */
  @Column(name = "amount", precision = 19, scale = 2, nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30)
  private TransactionType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private TransactionStatus status = TransactionStatus.SUCCESS;

  // ── External payment gateway (null for internal fund flows) ──────
  @Column(name = "payment_ref", length = 100)
  private String paymentRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "gateway_provider", length = 20)
  private PaymentProvider gatewayProvider;

  // ── Polymorphic reference: what business event triggered this? ───
  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", length = 30)
  private ReferenceType referenceType;

  @Column(name = "reference_id")
  private Long referenceId;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  // ── Ledger entries (accounting lines for this transaction) ───────
  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<LedgerEntry> entries = new ArrayList<>();

  // ── Status transitions ───────────────────────────────────────────

  public void markSuccess() {
    assertPending();
    this.status = TransactionStatus.SUCCESS;
  }

  public void markFailed() {
    assertPending();
    this.status = TransactionStatus.FAILED;
  }

  public void markCancelled() {
    assertPending();
    this.status = TransactionStatus.CANCELLED;
  }

  private void assertPending() {
    if (this.status != TransactionStatus.PENDING) {
      throw new InvalidTransactionStatusTransitionException(this.status);
    }
  }
}
