package com.mkwang.backend.modules.accounting.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CompanyFund — metadata record for the company's bank account and reconciliation reference.
 *
 * Singleton entity (id = 1). Tracks:
 *   - Company bank account info (bankName, bankAccount)
 *   - Last known external bank balance (manually entered by Accountant from bank statement)
 *   - Last statement date and who updated it
 *
 * Balance tracking is handled exclusively by Wallet(COMPANY_FUND, ownerId=1) via WalletService.
 * This entity does NOT maintain balance — it is metadata-only.
 *
 * Reconciliation:
 *   bankDiscrepancy = externalBankBalance - Wallet(COMPANY_FUND).balance  (expected = 0)
 */
@Entity
@Table(name = "company_funds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyFund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_account", length = 30)
    private String bankAccount;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    /**
     * Last known balance from the company's actual bank statement.
     * Updated manually by Accountant after reconciling with real bank.
     * Used to compute bankDiscrepancy in ReconciliationReport.
     */
    @Column(name = "external_bank_balance", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal externalBankBalance = BigDecimal.ZERO;

    /**
     * Date of the most recent bank statement used to update externalBankBalance.
     */
    @Column(name = "last_statement_date")
    private LocalDate lastStatementDate;

    /**
     * ID of the user (Accountant/CFO) who last updated the bank statement figures.
     */
    @Column(name = "last_statement_updated_by")
    private Long lastStatementUpdatedBy;
}
