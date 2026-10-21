package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.AccountingJournalLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public class AccountingReportRepository {
    @PersistenceContext private EntityManager em;

    public List<Object[]> aggregate(Specification<AccountingJournalLine> spec) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(Object[].class);
        var root = query.from(AccountingJournalLine.class);
        query.multiselect(root.get("accountCode"), cb.sum(root.<BigDecimal>get("debitAmount")),
                cb.sum(root.<BigDecimal>get("creditAmount")));
        query.where(spec.toPredicate(root, query, cb));
        query.groupBy(root.get("accountCode"));
        return em.createQuery(query).getResultList();
    }
}
