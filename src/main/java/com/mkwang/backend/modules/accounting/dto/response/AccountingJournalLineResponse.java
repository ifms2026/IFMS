package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;

public record AccountingJournalLineResponse(
        Long id,
        String accountCode,
        String accountName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String effectDescription,
        Long advanceBalanceId,
        Long requestId,
        Long employeeId,
        Long projectId
) {}
