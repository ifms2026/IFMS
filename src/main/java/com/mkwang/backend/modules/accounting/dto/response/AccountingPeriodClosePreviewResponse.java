package com.mkwang.backend.modules.accounting.dto.response;

import java.util.List;

public record AccountingPeriodClosePreviewResponse(
        String period,
        boolean eligible,
        long journalCount,
        long invalidJournalCount,
        long missingSourceCount,
        long unmatchedTopupCount,
        List<String> blockingIssues,
        List<String> carryForwardNotes) {}
