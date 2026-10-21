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
        return (root, query, cb) -> {
            var exists = query.subquery(Long.class);
            var line = exists.from(com.mkwang.backend.modules.accounting.entity.AccountingJournalLine.class);
            exists.select(line.get("id")).where(cb.equal(line.get("journal"), root), cb.equal(line.get("projectId"), projectId));
            return cb.or(cb.equal(root.get("projectId"), projectId), cb.exists(exists));
        };
    }

    public static Specification<AccountingJournal> hasRequest(Long requestId) {
        return (root, query, cb) -> requestId == null ? null : cb.equal(root.get("requestId"), requestId);
    }

    public static Specification<AccountingJournal> filter(AccountingJournalEvent event, LocalDate from, LocalDate to,
            Long employeeId, Long projectId, Long requestId) {
        Specification<AccountingJournal> spec = Specification.where(null);
        if (event != null) spec = spec.and(hasEvent(event));
        if (from != null) spec = spec.and(postedOnOrAfter(from));
        if (to != null) spec = spec.and(postedOnOrBefore(to));
        if (employeeId != null) spec = spec.and(hasEmployee(employeeId));
        if (projectId != null) spec = spec.and(hasProject(projectId));
        if (requestId != null) spec = spec.and(hasRequest(requestId));
        return spec;
    }
}
