package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalItemResponse;
import com.mkwang.backend.modules.accounting.dto.response.AdvanceEmployeeSummaryResponse;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import com.mkwang.backend.modules.accounting.entity.Payslip;
import com.mkwang.backend.modules.request.entity.AdvanceBalance;
import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse;
import com.mkwang.backend.modules.wallet.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AccountingJournalService {
    void recordAdvanceDisbursed(Request request, AdvanceBalance balance, Transaction transaction);
    void recordExpenseVerified(Request request);
    void recordExpensePaid(Request request, Transaction transaction);
    void recordReimburseSettled(Request request, AdvanceBalance balance);
    void recordAdvanceReturned(AdvanceBalance balance, Transaction transaction, BigDecimal amount, String description);
    void recordPayrollSettlement(Payslip payslip, Transaction transaction, List<AdvanceSettlementAllocationResponse> allocations);

    PageResponse<AccountingJournalItemResponse> getJournals(
            AccountingJournalEvent event, LocalDate from, LocalDate to, int page, int limit);
    AccountingJournalDetailResponse getJournalDetail(Long journalId);
    List<AdvanceEmployeeSummaryResponse> getOutstandingAdvancesByEmployee();
}
