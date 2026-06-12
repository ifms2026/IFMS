package com.mkwang.backend.modules.audit.publisher;

import com.mkwang.backend.modules.audit.dto.AuditMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * AuditPublisher — tách việc gọi RabbitMQ ra khỏi Hibernate thread.
 * <p>
 * Vấn đề: {@code rabbitTemplate.convertAndSend()} trong {@code FinancialAuditListener}
 * chạy TRỰC TIẾP trên Hibernate commit thread = request thread của business operation.
 * Điều này cộng thêm latency mạng RabbitMQ vào mỗi response.
 * <p>
 * Giải pháp: {@code @Async("auditPublishExecutor")} → Spring gọi method này trên
 * dedicated thread pool riêng → Hibernate thread trả về NGAY, không chờ RabbitMQ.
 * <p>
 * Thread pool "auditPublishExecutor" được định nghĩa trong {@code AuditRabbitMQConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.audit.exchange}")
    private String exchange;

    @Value("${spring.rabbitmq.audit.routing-key}")
    private String routingKey;

    /**
     * Gửi AuditMessageDTO vào RabbitMQ KHÔNG ĐỒNG BỘ.
     * Hibernate commit thread KHÔNG bị block chờ broker ack.
     */
    @Async("auditPublishExecutor")
    public void publishAsync(AuditMessageDTO dto) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, dto);
            log.trace("[AuditPublisher] Published async: action={}, entity={}#{}, actor={}",
                    dto.action(), dto.entityName(), dto.entityId(), dto.actorId());
        } catch (Exception ex) {
            // KHÔNG throw — audit failure không ảnh hưởng business
            log.error("[AuditPublisher] RabbitMQ send failed: {}", ex.getMessage(), ex);
        }
    }
}
