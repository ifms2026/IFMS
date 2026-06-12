package com.mkwang.backend.modules.audit.entity;

import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * AuditLog entity — Data-level audit trail (Append-Only).
 * Ghi nhận mọi thao tác INSERT/UPDATE/DELETE tự động qua Hibernate PostCommit
 * Listeners.
 *
 * Append-Only: không UPDATE/DELETE — mọi cột đều có updatable = false.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        // Tối ưu tốc độ truy vấn cho Admin Dashboard
        @Index(name = "idx_audit_trace", columnList = "trace_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_name, entity_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã gom nhóm giao dịch (Trace ID).
     * Cực kỳ quan trọng trong FinTech để nhóm các log của Order, Payment, Wallet...
     * vào chung 1 thao tác nghiệp vụ duy nhất.
     */
    @Column(name = "trace_id", length = 36, updatable = false)
    private String traceId;

    /**
     * The user who performed the action.
     * Nullable — system-triggered actions may have no actor.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    /**
     * Classification of the action performed.
     * e.g. INSERT, UPDATE, DELETE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50, updatable = false)
    private AuditAction action;

    /**
     * Name of the affected table / entity.
     * e.g. "Wallet", "Order", "SystemConfig"
     */
    @Column(name = "entity_name", nullable = false, length = 100, updatable = false)
    private String entityName;

    /**
     * ID of the affected row (stored as String to support composite keys).
     */
    @Column(name = "entity_id", nullable = false, length = 100, updatable = false)
    private String entityId;

    /**
     * Snapshot of the entity state BEFORE the change (JSON).
     * Nullable for INSERT actions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb", updatable = false)
    private String oldValues;

    /**
     * Snapshot of the entity state AFTER the change (JSON).
     * Nullable for DELETE actions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb", updatable = false)
    private String newValues;

    /**
     * Exact timestamp when the action occurred.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
