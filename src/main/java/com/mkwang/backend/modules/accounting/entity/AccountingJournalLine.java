package com.mkwang.backend.modules.accounting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "accounting_journal_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uc_accounting_journal_line_number", columnNames = {"journal_id", "line_number"})
}, indexes = {
        @Index(name = "idx_accounting_journal_line_advance", columnList = "advance_balance_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingJournalLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_id", nullable = false, updatable = false)
    private AccountingJournal journal;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "account_code", nullable = false, length = 40)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "debit_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "effect_description", nullable = false, length = 500)
    private String effectDescription;

    @Column(name = "advance_balance_id")
    private Long advanceBalanceId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "project_id")
    private Long projectId;
}
