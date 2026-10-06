package com.mkwang.backend.modules.wallet.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerItemResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerSummaryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantTransactionDetailResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantWalletTransactionResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalItemResponse;
import com.mkwang.backend.modules.accounting.dto.response.AdvanceEmployeeSummaryResponse;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;

import java.time.LocalDate;
import java.util.List;

public interface AccountantLedgerService {

    PageResponse<AccountantLedgerItemResponse> getLedger(
            TransactionType type, TransactionStatus status, ReferenceType referenceType,
            LocalDate from, LocalDate to, int page, int limit);

    AccountantLedgerSummaryResponse getLedgerSummary(TransactionType type, TransactionStatus status,
            ReferenceType referenceType, LocalDate from, LocalDate to);

    PageResponse<AccountantWalletTransactionResponse> getWalletTransactions(TransactionType type,
            TransactionStatus status, ReferenceType referenceType, LocalDate from, LocalDate to, int page, int limit);

    PageResponse<AccountingJournalItemResponse> getJournals(AccountingJournalEvent event,
            LocalDate from, LocalDate to, int page, int limit);

    AccountingJournalDetailResponse getJournalDetail(Long journalId);

    List<AdvanceEmployeeSummaryResponse> getOutstandingAdvancesByEmployee();

    AccountantTransactionDetailResponse getTransactionDetail(Long transactionId);
}
