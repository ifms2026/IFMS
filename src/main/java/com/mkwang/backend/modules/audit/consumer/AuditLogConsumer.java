package com.mkwang.backend.modules.audit.consumer;

import com.mkwang.backend.modules.audit.dto.AuditMessageDTO;
import com.mkwang.backend.modules.audit.entity.AuditAction;
import com.mkwang.backend.modules.audit.entity.AuditLog;
import com.mkwang.backend.modules.audit.repository.AuditLogRepository;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuditLogConsumer — nhận AuditMessageDTO và persist vào audit_logs.
 * <p>
 * Performance: dùng {@code userRepository.getReferenceById(actorId)}
 * thay vì {@code userRepository.findById(actorId)}.
 * <p>
 * Lý do:
 * <ul>
 *   <li>getReferenceById() → gọi EntityManager.getReference() nội bộ → trả về Hibernate proxy</li>
 *   <li>Không có SELECT bảng users — chỉ set FK actor_id trực tiếp</li>
 *   <li>An toàn hơn inject EntityManager thẳng qua constructor (thiếu @PersistenceContext)</li>
 * </ul>
 * </p>
 * Lưu ý: actorId = null là hợp lệ cho system/scheduled actions và public endpoints (login).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // ── Main listener ────────────────────────────────────────────

    @RabbitListener(queues = "${spring.rabbitmq.audit.queue}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(AuditMessageDTO dto) {
        // getReferenceById() = Hibernate proxy — KHÔNG SELECT users table,
        // chỉ dùng ID để set FK actor_id trong câu INSERT audit_logs
        User actor = dto.actorId() != null
                ? userRepository.getReferenceById(dto.actorId())
                : null;

        AuditLog auditLog = AuditLog.builder()
                .traceId(dto.traceId())
                .actor(actor)
                .action(AuditAction.valueOf(dto.action()))
                .entityName(dto.entityName())
                .entityId(dto.entityId())
                .oldValues(dto.oldValues())
                .newValues(dto.newValues())
                .build();

        auditLogRepository.save(auditLog);

        log.debug("[AuditLogConsumer] Saved: traceId={}, action={}, entity={}#{}, actor={}",
                dto.traceId(), dto.action(), dto.entityName(), dto.entityId(),
                dto.actorId() != null ? dto.actorId() : "SYSTEM");
    }

    // ── DLQ listener ─────────────────────────────────────────────

    @RabbitListener(queues = "${spring.rabbitmq.audit.dlq}")
    public void consumeDLQ(Message rawMessage) {
        log.warn("[AuditLogConsumer][DLQ] Audit failed after all retries. messageId={}, body={}",
                rawMessage.getMessageProperties().getMessageId(),
                new String(rawMessage.getBody()));
    }
}
