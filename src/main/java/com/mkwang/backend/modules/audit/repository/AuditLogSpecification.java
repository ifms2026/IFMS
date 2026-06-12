package com.mkwang.backend.modules.audit.repository;

import com.mkwang.backend.modules.audit.entity.AuditAction;
import com.mkwang.backend.modules.audit.entity.AuditLog;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLog> hasActor(Long actorId) {
        return (root, query, cb) ->
                actorId == null ? null : cb.equal(root.get("actor").get("id"), actorId);
    }

    public static Specification<AuditLog> hasAction(AuditAction action) {
        return (root, query, cb) ->
                action == null ? null : cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> hasEntityName(String entityName) {
        return (root, query, cb) ->
                (entityName == null || entityName.isBlank()) ? null
                        : cb.equal(root.get("entityName"), entityName);
    }

    public static Specification<AuditLog> fromDate(LocalDateTime from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> toDate(LocalDateTime to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    // Fetch actor in data queries only — skipped for count queries to avoid JOIN issues
    private static Specification<AuditLog> fetchActor() {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("actor", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<AuditLog> filter(
            Long actorId, AuditAction action, String entityName,
            LocalDateTime from, LocalDateTime to) {
        return Specification.where(fetchActor())
                .and(hasActor(actorId))
                .and(hasAction(action))
                .and(hasEntityName(entityName))
                .and(fromDate(from))
                .and(toDate(to));
    }
}
