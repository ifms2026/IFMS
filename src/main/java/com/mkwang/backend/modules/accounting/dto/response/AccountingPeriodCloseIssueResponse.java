package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;

public record AccountingPeriodCloseIssueResponse(
        String code,
        String severity,
        Long journalId,
        String journalCode,
        String eventType,
        String sourceType,
        String sourceId,
        BigDecimal amount,
        String message,
        String actionHint) {}
