package com.mkwang.backend.modules.wallet.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WithdrawRequest — Represents a user's request to withdraw funds to their bank
 * account.
 *
 * Lifecycle:
 * amount <= role limit ⇒ createRequest auto-calls MockBank ⇒ COMPLETED | FAILED
 * amount > role limit ⇒ PENDING ⇒ accountant executeWithdraw ⇒ COMPLETED |
 * FAILED
 * any PENDING ⇒ cancelRequest (user) ⇒ CANCELLED
 * any PENDING ⇒ rejectRequest (accountant) ⇒ REJECTED
 *
 * Bank info snapshot-ed from UserProfile at submission time.
 */
@Entity
@Table(name = "withdrawal_requests", indexes = {
        @Index(name = "idx_wr_user_id", columnList = "user_id"),
        @Index(name = "idx_wr_status", columnList = "status"),
        @Index(name = "idx_wr_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable code — WD-{YYYY}-{SEQ:06d}. Generated at application layer.
     */
    @Column(name = "withdraw_code", unique = true, nullable = false, length = 30)
    private String withdrawCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    // ── Snapshot of UserProfile bank info at submission time ─────────────

    /** Bank account number of recipient (user's real bank account). */
    @Column(name = "credit_account", nullable = false, length = 30)
    private String creditAccount;

    /** Account holder name (from UserProfile.bankAccountOwner). */
    @Column(name = "credit_account_name", nullable = false, length = 100)
    private String creditAccountName;

    /** Bank code, e.g. "VCB", "TCB". */
    @Column(name = "credit_bank_code", nullable = false, length = 20)
    private String creditBankCode;

    /** Full bank name, e.g. "Vietcombank". */
    @Column(name = "credit_bank_name", nullable = false, length = 100)
    private String creditBankName;

    @Column(name = "user_note", length = 500)
    private String userNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WithdrawStatus status;

    // ── Filled after MockBank response ───────────────────────────────────

    /**
     * MockBank transaction ID, e.g. "VCB20260405103000000001". Filled on COMPLETED.
     */
    @Column(name = "bank_transaction_id", length = 50)
    private String bankTransactionId;

    /**
     * Note from operator (accountant or system auto-execute) when
     * executing/rejecting.
     */
    @Column(name = "accountant_note", length = 500)
    private String accountantNote;

    /**
     * ID of user who executed or rejected this request (null if auto-executed by
     * system).
     */
    @Column(name = "executed_by")
    private Long executedBy;

    /** When the execution or rejection took place. */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** FK → transactions.id. Filled on COMPLETED. */
    @Column(name = "transaction_id")
    private Long transactionId;

    /**
     * MockBank responseCode + responseMessage when FAILED, e.g. "[96] Loi he
     * thong".
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
