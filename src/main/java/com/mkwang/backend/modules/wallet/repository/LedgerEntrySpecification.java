package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.LedgerEntry;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;

public class LedgerEntrySpecification {

    private LedgerEntrySpecification() {}

    public static Specification<LedgerEntry> hasTransactionType(TransactionType type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("transaction").get("type"), type);
    }

    public static Specification<LedgerEntry> hasTransactionStatus(TransactionStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("transaction").get("status"), status);
    }

    public static Specification<LedgerEntry> hasReferenceType(ReferenceType referenceType) {
        return (root, query, cb) ->
                referenceType == null ? null : cb.equal(root.get("transaction").get("referenceType"), referenceType);
    }

    public static Specification<LedgerEntry> createdAfter(LocalDate from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
    }

    public static Specification<LedgerEntry> createdBefore(LocalDate to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to.atTime(LocalTime.MAX));
    }

    public static Specification<LedgerEntry> filter(
            TransactionType type, TransactionStatus status, ReferenceType referenceType,
            LocalDate from, LocalDate to) {
        return Specification.where(hasTransactionType(type))
                .and(hasTransactionStatus(status))
                .and(hasReferenceType(referenceType))
                .and(createdAfter(from))
                .and(createdBefore(to));
    }
}
