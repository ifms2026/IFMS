package com.mkwang.backend.modules.accounting.mapper;

import com.mkwang.backend.modules.accounting.dto.response.*;
import com.mkwang.backend.modules.accounting.entity.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Comparator;

@Component
public class AccountingJournalMapper {
    public BigDecimal debit(AccountingJournal journal) {
        return journal.getLines().stream().map(AccountingJournalLine::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal credit(AccountingJournal journal) {
        return journal.getLines().stream().map(AccountingJournalLine::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public boolean balanced(AccountingJournal journal) {
        BigDecimal debit = debit(journal);
        return journal.getLines().size() >= 2 && debit.signum() > 0 && debit.compareTo(credit(journal)) == 0
                && debit.compareTo(journal.getTotalAmount()) == 0;
    }
    public AccountingJournalItemResponse toItem(AccountingJournal j) {
        return new AccountingJournalItemResponse(j.getId(), j.getJournalCode(), j.getEventType(), j.getPostingDate(),
                j.getPostingPeriod(), j.getDescription(), j.getSourceType(), j.getSourceId(), j.getRequestId(),
                j.getAdvanceBalanceId(), j.getEmployeeId(), j.getEmployeeName(), j.getProjectId(), j.getProjectName(),
                j.getWalletTransactionId(), j.getTotalAmount(), balanced(j), j.getCreatedByUserId(), j.getCreatedByName(), j.getCreatedAt());
    }
    public AccountingJournalLineResponse toLine(AccountingJournalLine l) {
        return new AccountingJournalLineResponse(l.getId(), l.getAccountCode(), l.getAccountName(), l.getDebitAmount(),
                l.getCreditAmount(), l.getEffectDescription(), l.getAdvanceBalanceId(), l.getRequestId(), l.getEmployeeId(),
                l.getProjectId(), l.getDepartmentId(), l.getPhaseId(), l.getCategoryId(), l.getProjectName(), l.getPhaseName(), l.getCategoryName());
    }
    public AccountingJournalDetailResponse toDetail(AccountingJournal j) {
        return new AccountingJournalDetailResponse(j.getId(), j.getJournalCode(), j.getEventType(), j.getPostingDate(),
                j.getPostingPeriod(), j.getDescription(), j.getSourceType(), j.getSourceId(), j.getRequestId(),
                j.getAdvanceBalanceId(), j.getEmployeeId(), j.getEmployeeName(), j.getProjectId(), j.getProjectName(),
                j.getWalletTransactionId(), j.getTotalAmount(), balanced(j), debit(j), credit(j),
                j.getLines().stream().sorted(Comparator.comparing(AccountingJournalLine::getLineNumber)).map(this::toLine).toList(),
                j.getCreatedByUserId(), j.getCreatedByName(), j.getCreatedAt(), j.getBusinessDate(), j.getDepartmentId(),
                j.getDepartmentName(), j.getPhaseId(), j.getPhaseName(), j.getCategoryId(), j.getCategoryName(),
                j.getSourceSnapshot(), j.getOriginalJournalId());
    }
}
