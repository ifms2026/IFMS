package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.AccountingJournal;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class AccountingJournalSpecification {
    private AccountingJournalSpecification() {}

    public static Specification<AccountingJournal> hasEvent(AccountingJournalEvent event) {
        return (root, query, cb) -> event == null ? null : cb.equal(root.get("eventType"), event);
    }

    public static Specification<AccountingJournal> postedOnOrAfter(LocalDate from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("postingDate"), from);
    }

    public static Specification<AccountingJournal> postedOnOrBefore(LocalDate to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("postingDate"), to);
    }

    public static Specification<AccountingJournal> filter(AccountingJournalEvent event, LocalDate from, LocalDate to) {
        return Specification.where(hasEvent(event)).and(postedOnOrAfter(from)).and(postedOnOrBefore(to));
    }
}
