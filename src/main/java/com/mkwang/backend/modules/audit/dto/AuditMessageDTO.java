package com.mkwang.backend.modules.audit.dto;

/**
 * AuditMessageDTO — DTO gửi qua RabbitMQ từ FinancialAuditListener đến AuditLogConsumer.
 *
 * @param traceId    UUID gom nhóm tất cả log của 1 HTTP request/transaction
 * @param action     "INSERT" | "UPDATE" | "DELETE"
 * @param entityName tên class entity (simple name)
 * @param entityId   ID dạng String
 * @param actorId    ID người thao tác (null nếu hệ thống)
 * @param oldValues  JSON string trạng thái trước thay đổi (null cho INSERT)
 * @param newValues  JSON string trạng thái sau thay đổi (null cho DELETE)
 */
public record AuditMessageDTO(
        String traceId,
        String action,
        String entityName,
        String entityId,
        Long actorId,
        String oldValues,
        String newValues
) {}
