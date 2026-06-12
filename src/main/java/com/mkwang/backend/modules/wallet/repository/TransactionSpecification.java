package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TransactionSpecification {

    private TransactionSpecification() {}

    public static Specification<Transaction> hasType(TransactionType type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> hasReferenceType(ReferenceType referenceType) {
        return (root, query, cb) ->
                referenceType == null ? null : cb.equal(root.get("referenceType"), referenceType);
    }

    public static Specification<Transaction> createdAfter(LocalDate from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
    }

    public static Specification<Transaction> createdBefore(LocalDate to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), LocalDateTime.of(to, LocalTime.MAX));
    }

    public static Specification<Transaction> filter(
            TransactionType type, TransactionStatus status, ReferenceType referenceType,
            LocalDate from, LocalDate to) {
        return Specification.where(hasType(type))
                .and(hasStatus(status))
                .and(hasReferenceType(referenceType))
                .and(createdAfter(from))
                .and(createdBefore(to));
    }
}
