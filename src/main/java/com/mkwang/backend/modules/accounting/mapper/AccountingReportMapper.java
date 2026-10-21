package com.mkwang.backend.modules.accounting.mapper;

import com.mkwang.backend.modules.accounting.dto.response.AccountLedgerResponse.AccountLedgerEntryResponse;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalLine;
import org.springframework.stereotype.Component;

@Component
public class AccountingReportMapper {
    public AccountLedgerEntryResponse toEntry(AccountingJournalLine line) {
        var journal = line.getJournal();
        return new AccountLedgerEntryResponse(journal.getId(), journal.getJournalCode(), journal.getPostingDate(),
                line.getEffectDescription(), line.getEmployeeId(), line.getProjectId(), line.getProjectName(),
                line.getPhaseName(), line.getCategoryName(), line.getDebitAmount(), line.getCreditAmount());
    }
}
