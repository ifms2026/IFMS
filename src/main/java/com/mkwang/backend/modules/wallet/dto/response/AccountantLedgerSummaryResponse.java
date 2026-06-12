package com.mkwang.backend.modules.wallet.dto.response;

import java.math.BigDecimal;

/**
 * Aggregate summary for the accountant ledger summary view.
 * All figures are scoped to the CompanyFund wallet (COMPANY_FUND, ownerId=1).
 * totalInflow / totalOutflow are filtered by the requested date range when provided.
 */
public record AccountantLedgerSummaryResponse(
        BigDecimal currentBalance,
        BigDecimal totalInflow,
        BigDecimal totalOutflow,
        long transactionCount
) {}
