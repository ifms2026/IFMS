package com.mkwang.backend.modules.audit.entity;

/**
 * AuditAction — phân loại thao tác dữ liệu ở tầng Hibernate.
 * Đơn giản hóa về 3 loại cơ bản, đồng nhất với SQL DML operations.
 */
public enum AuditAction {
    INSERT,
    UPDATE,
    DELETE
}
