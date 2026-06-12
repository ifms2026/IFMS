package com.mkwang.backend.modules.audit.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.mkwang.backend.modules.audit.entity.AuditAction;
import com.mkwang.backend.modules.audit.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        AuditAction action,
        String entityName,
        String entityId,
        @JsonRawValue String oldValues,
        @JsonRawValue String newValues,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActor() != null ? log.getActor().getId() : null,
                log.getActor() != null ? log.getActor().getFullName() : null,
                log.getAction(),
                log.getEntityName(),
                log.getEntityId(),
                log.getOldValues(),
                log.getNewValues(),
                log.getCreatedAt()
        );
    }
}
