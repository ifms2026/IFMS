package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AccountantWalletTransactionResponse(
        Long id,
        String transactionCode,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        ReferenceType referenceType,
        Long referenceId,
        String description,
        LocalDateTime timestamp,
        List<AccountantLedgerEntryResponse> walletMovements
) {}
