package com.mkwang.backend.modules.notification.consumer;

import com.mkwang.backend.common.exception.InternalSystemException;
import com.mkwang.backend.common.dto.SseEvent;
import com.mkwang.backend.common.sse.SseEventType;
import com.mkwang.backend.common.sse.SseService;
import com.mkwang.backend.modules.notification.entity.Notification;
import com.mkwang.backend.modules.notification.entity.NotificationType;
import com.mkwang.backend.modules.notification.mapper.NotificationMapper;
import com.mkwang.backend.modules.notification.publisher.NotificationEvent;
import com.mkwang.backend.modules.notification.repository.NotificationRepository;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationConsumer — nhận NotificationEvent từ RabbitMQ, thực hiện 2 việc:
 * <ol>
 *   <li>Persist vào DB (đảm bảo user offline vẫn nhận được khi online lại)</li>
 *   <li>Push real-time qua SSE nếu user đang online (best-effort)</li>
 * </ol>
 *
 * Persist TRƯỚC push — nếu SSE push fail, notification vẫn còn trong DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationRepository  notificationRepository;
    private final NotificationMapper      notificationMapper;
    private final UserService             userService;
    private final SseService              sseService;

    @RabbitListener(queues = "${spring.rabbitmq.notification.queue}", concurrency = "2-5")
    @Transactional
    public void consume(NotificationEvent event) {
        // 1. Persist vào DB
        User user = userService.getUserById(event.userId());

        NotificationType type;
        try {
            type = NotificationType.valueOf(event.type());
        } catch (IllegalArgumentException e) {
            throw new InternalSystemException("Unknown NotificationType: " + event.type());
        }

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(event.title())
                .message(event.message())
                .refId(event.refId())
                .refType(event.refType())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.debug("[NotificationConsumer] Persisted notification id={} type={} userId={}",
                saved.getId(), event.type(), event.userId());

        // 2. SSE push — best-effort (user có thể offline)
        try {
            sseService.sendToUser(event.userId(), SseEvent.builder()
                    .event(SseEventType.NOTIFICATION)
                    .data(notificationMapper.toDto(saved))
                    .build());
            log.debug("[NotificationConsumer] SSE pushed to userId={}", event.userId());
        } catch (Exception e) {
            log.warn("[NotificationConsumer] SSE push failed for userId={}: {}",
                    event.userId(), e.getMessage());
        }
    }

    @RabbitListener(queues = "${spring.rabbitmq.notification.dlq}")
    public void consumeDLQ(Message rawMessage) {
        log.warn("[NotificationConsumer][DLQ] Notification FAILED after all retries. messageId={}",
                rawMessage.getMessageProperties().getMessageId());
    }
}
