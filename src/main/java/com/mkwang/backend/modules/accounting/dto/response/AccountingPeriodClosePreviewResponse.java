package com.mkwang.backend.modules.accounting.dto.response;

import java.util.List;

public record AccountingPeriodClosePreviewResponse(
        String period,
        boolean eligible,
        long journalCount,
        long invalidJournalCount,
        long missingSourceCount,
        long unmatchedTopupCount,
        long invalidSourceLinkCount,
        long sourceAmountMismatchCount,
        long invalidAdvanceBalanceCount,
        long issueTotal,
        int issuePage,
        int issueSize,
        java.util.List<AccountingPeriodCloseIssueResponse> issues,
        List<String> blockingIssues,
        List<String> carryForwardNotes) {}
