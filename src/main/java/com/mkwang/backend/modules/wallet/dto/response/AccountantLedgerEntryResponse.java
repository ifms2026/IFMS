package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.TransactionDirection;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountantLedgerEntryResponse(
        Long id,
        String transactionCode,
        TransactionDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        WalletOwnerType walletOwnerType,
        Long walletOwnerId,
        LocalDateTime createdAt
) {}
