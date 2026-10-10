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

    public static Specification<AccountingJournal> hasEmployee(Long employeeId) {
        return (root, query, cb) -> employeeId == null ? null : cb.equal(root.get("employeeId"), employeeId);
    }

    public static Specification<AccountingJournal> hasProject(Long projectId) {
        return (root, query, cb) -> projectId == null ? null : cb.equal(root.get("projectId"), projectId);
    }

    public static Specification<AccountingJournal> hasRequest(Long requestId) {
        return (root, query, cb) -> requestId == null ? null : cb.equal(root.get("requestId"), requestId);
    }

    public static Specification<AccountingJournal> filter(AccountingJournalEvent event, LocalDate from, LocalDate to,
            Long employeeId, Long projectId, Long requestId) {
        return Specification.where(hasEvent(event)).and(postedOnOrAfter(from)).and(postedOnOrBefore(to))
                .and(hasEmployee(employeeId)).and(hasProject(projectId)).and(hasRequest(requestId));
    }
}
