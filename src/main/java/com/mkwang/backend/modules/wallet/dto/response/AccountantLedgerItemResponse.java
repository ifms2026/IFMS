package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.TransactionDirection;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountantLedgerItemResponse(
        Long id,
        String transactionCode,
        TransactionType type,
        TransactionStatus status,
        TransactionDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        WalletOwnerType walletOwnerType,
        Long ownerId,
        LocalDateTime timestamp
) {}
