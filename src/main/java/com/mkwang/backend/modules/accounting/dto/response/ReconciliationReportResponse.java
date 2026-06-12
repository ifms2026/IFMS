package com.mkwang.backend.modules.accounting.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ReconciliationReport — snapshot of the system's financial integrity at a point in time.
 *
 * Two checks are performed:
 *
 * 1. System Integrity Check (internal invariant):
 *    FLOAT_MAIN.balance should equal SUM(all wallets except FLOAT_MAIN).
 *    If systemDiscrepancy ≠ 0, a mutation occurred outside WalletService.
 *
 * 2. Bank Statement Check (external reconciliation):
 *    companyFundBalance should equal externalBankBalance.
 *    If bankDiscrepancy ≠ 0, the bank statement hasn't been updated or
 *    there is unrecorded activity in the external bank account.
 */
@Data
@Builder
public class ReconciliationReportResponse {

    private LocalDateTime generatedAt;

    // ── System Integrity Check (FLOAT_MAIN invariant) ────────────────────────

    /** Stored running total — FLOAT_MAIN.balance. */
    private BigDecimal floatMainBalance;

    /** SELECT SUM(balance) FROM wallets WHERE owner_type != 'FLOAT_MAIN'. */
    private BigDecimal computedWalletSum;

    /** floatMainBalance − computedWalletSum.  Expected = 0. */
    private BigDecimal systemDiscrepancy;

    /** true if systemDiscrepancy == 0. */
    private boolean systemIntegrityValid;

    // ── Wallet Breakdown (where the money is inside IFMS) ────────────────────

    private BigDecimal companyFundBalance;
    private BigDecimal totalDeptWallets;
    private BigDecimal totalProjectWallets;
    private BigDecimal totalUserWallets;

    // ── Bank Statement Check (external reconciliation) ───────────────────────

    /** Last known balance from the physical bank statement. */
    private BigDecimal externalBankBalance;
    private LocalDate  lastStatementDate;

    /** companyFundBalance − externalBankBalance.  Expected = 0. */
    private BigDecimal bankDiscrepancy;
}
