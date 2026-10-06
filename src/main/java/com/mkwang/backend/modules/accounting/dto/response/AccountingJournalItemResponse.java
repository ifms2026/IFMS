package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountingJournalItemResponse(
        Long id,
        String journalCode,
        AccountingJournalEvent eventType,
        LocalDate postingDate,
        String postingPeriod,
        String description,
        String sourceType,
        String sourceId,
        Long requestId,
        Long advanceBalanceId,
        Long employeeId,
        String employeeName,
        Long projectId,
        String projectName,
        Long walletTransactionId,
        BigDecimal totalAmount,
        boolean balanced
) {}
