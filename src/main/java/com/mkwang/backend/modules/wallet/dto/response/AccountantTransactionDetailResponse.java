package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.PaymentProvider;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full transaction detail for the accountant ledger.
 * amount is signed from the CompanyFund wallet perspective (same rule as list view).
 * walletOwnerType/walletOwnerId identify the primary (DEBIT) wallet involved.
 * Boundary transactions (SYSTEM_TOPUP) have only 1 ledgerEntry.
 */
public record AccountantTransactionDetailResponse(
        Long id,
        String transactionCode,
        String paymentRef,
        PaymentProvider gatewayProvider,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal balanceAfter,
        ReferenceType referenceType,
        Long referenceId,
        WalletOwnerType walletOwnerType,
        Long walletOwnerId,
        String description,
        List<AccountantLedgerEntryResponse> ledgerEntries,
        LocalDateTime createdAt
) {}
