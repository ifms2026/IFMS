package com.mkwang.backend.modules.wallet.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerItemResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerSummaryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantTransactionDetailResponse;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;

import java.time.LocalDate;

public interface AccountantLedgerService {

    PageResponse<AccountantLedgerItemResponse> getLedger(
            TransactionType type, TransactionStatus status, ReferenceType referenceType,
            LocalDate from, LocalDate to, int page, int limit);

    AccountantLedgerSummaryResponse getLedgerSummary(LocalDate from, LocalDate to);

    AccountantTransactionDetailResponse getTransactionDetail(Long transactionId);
}
