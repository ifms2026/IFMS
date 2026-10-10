package com.mkwang.backend.modules.accounting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounting_journals", uniqueConstraints = {
        @UniqueConstraint(name = "uc_accounting_journal_event_source",
                columnNames = {"event_type", "source_type", "source_id"})
}, indexes = {
        @Index(name = "idx_accounting_journal_posting_date", columnList = "posting_date"),
        @Index(name = "idx_accounting_journal_request", columnList = "request_id"),
        @Index(name = "idx_accounting_journal_advance", columnList = "advance_balance_id"),
        @Index(name = "idx_accounting_journal_wallet_txn", columnList = "wallet_transaction_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingJournal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_code", nullable = false, unique = true, length = 40)
    private String journalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AccountingJournalEvent eventType;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "posting_period", nullable = false, length = 7)
    private String postingPeriod;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 80)
    private String sourceId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "advance_balance_id")
    private Long advanceBalanceId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "wallet_transaction_id")
    private Long walletTransactionId;

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AccountingJournalLine> lines = new ArrayList<>();
}
