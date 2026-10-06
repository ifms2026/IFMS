package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdvanceActivityResponse(
        Long journalId,
        String journalCode,
        AccountingJournalEvent eventType,
        LocalDate postingDate,
        String description,
        BigDecimal amount
) {}
