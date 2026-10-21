package com.mkwang.backend.modules.accounting.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CompanyFundResponse {
    private String bankComparisonStatus;
    private BigDecimal corporateBalanceAtStatement;
    private BigDecimal pendingCorporateAmount;
    private java.time.Instant statementAsOf;
    private String statementEvidenceRef;
    private boolean bankScopeConfirmed;

    private Long id;
    private String bankName;
    private String bankAccount;

    /** Current balance tracked by Wallet(COMPANY_FUND) — authoritative. */
    private BigDecimal currentWalletBalance;

    /** Last known external bank balance entered by Accountant. */
    private BigDecimal externalBankBalance;

    /** currentWalletBalance − externalBankBalance (expected = 0). */
    private BigDecimal bankDiscrepancy;

    private LocalDate lastStatementDate;
    private String lastStatementUpdatedBy;
}
