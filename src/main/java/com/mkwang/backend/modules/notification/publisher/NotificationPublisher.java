package com.mkwang.backend.modules.notification.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.notification.exchange}")
    private String exchange;

    @Value("${spring.rabbitmq.notification.routing-key}")
    private String routingKey;

    public void publish(NotificationEvent event) {
        log.debug("[NotificationPublisher] Publishing notification type={} to userId={}",
                event.type(), event.userId());
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
