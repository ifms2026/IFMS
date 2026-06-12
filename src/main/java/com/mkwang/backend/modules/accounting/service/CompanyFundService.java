package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.modules.accounting.dto.response.CompanyFundResponse;
import com.mkwang.backend.modules.accounting.dto.response.ReconciliationReportResponse;
import com.mkwang.backend.modules.accounting.dto.request.SystemTopupRequest;
import com.mkwang.backend.modules.accounting.dto.request.UpdateBankStatementRequest;
import com.mkwang.backend.modules.wallet.dto.response.TransactionResponse;

public interface CompanyFundService {

    /**
     * Get company fund metadata and current wallet balance.
     */
    CompanyFundResponse getCompanyFund();

    /**
     * Record an external bank transfer that increases the company fund.
     * Calls WalletService.systemTopup() which also updates FLOAT_MAIN.
     */
    TransactionResponse topup(SystemTopupRequest request);

    /**
     * Update the bank statement figures for external reconciliation.
     * Records what the actual bank account shows (entered manually by Accountant).
     */
    CompanyFundResponse updateBankStatement(UpdateBankStatementRequest request);

    /**
     * Generate a full reconciliation report:
     * - FLOAT_MAIN invariant check (internal integrity)
     * - Wallet breakdown by type
     * - Bank statement comparison (external reconciliation)
     */
    ReconciliationReportResponse getReconciliationReport();
}
