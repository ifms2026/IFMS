package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.AccountingJournalLine;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;

public final class AccountingJournalLineSpecification {
    private AccountingJournalLineSpecification() {}
    public static Specification<AccountingJournalLine> account(String code) {
        return (root, query, cb) -> cb.equal(root.get("accountCode"), code);
    }
    public static Specification<AccountingJournalLine> from(LocalDate date) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("journal").get("postingDate"), date);
    }
    public static Specification<AccountingJournalLine> to(LocalDate date) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("journal").get("postingDate"), date);
    }
    public static Specification<AccountingJournalLine> dimension(String field, Long id) {
        return (root, query, cb) -> cb.equal(root.get(field), id);
    }
    public static Specification<AccountingJournalLine> event(AccountingJournalEvent event) {
        return (root, query, cb) -> cb.equal(root.get("journal").get("eventType"), event);
    }
    public static Specification<AccountingJournalLine> before(AccountingJournalLine first) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("journal").get("postingDate"), first.getJournal().getPostingDate()),
                cb.and(cb.equal(root.get("journal").get("postingDate"), first.getJournal().getPostingDate()),
                        cb.lessThan(root.get("journal").get("id"), first.getJournal().getId())),
                cb.and(cb.equal(root.get("journal").get("id"), first.getJournal().getId()),
                        cb.lessThan(root.get("lineNumber"), first.getLineNumber())));
    }
    public static Specification<AccountingJournalLine> filter(String account, LocalDate from, LocalDate to,
            Long employeeId, Long projectId, Long departmentId, Long phaseId, Long categoryId) {
        return filter(account, from, to, employeeId, projectId, departmentId, phaseId, categoryId, null);
    }
    public static Specification<AccountingJournalLine> filter(String account, LocalDate from, LocalDate to,
            Long employeeId, Long projectId, Long departmentId, Long phaseId, Long categoryId,
            AccountingJournalEvent eventType) {
        Specification<AccountingJournalLine> spec = Specification.where(null);
        if (account != null) spec = spec.and(account(account));
        if (from != null) spec = spec.and(from(from));
        if (to != null) spec = spec.and(to(to));
        if (employeeId != null) spec = spec.and(dimension("employeeId", employeeId));
        if (projectId != null) spec = spec.and(dimension("projectId", projectId));
        if (departmentId != null) spec = spec.and(dimension("departmentId", departmentId));
        if (phaseId != null) spec = spec.and(dimension("phaseId", phaseId));
        if (categoryId != null) spec = spec.and(dimension("categoryId", categoryId));
        if (eventType != null) spec = spec.and(event(eventType));
        return spec;
    }
}
