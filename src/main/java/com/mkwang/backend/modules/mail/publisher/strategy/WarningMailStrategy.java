package com.mkwang.backend.modules.mail.publisher.strategy;

import com.mkwang.backend.modules.mail.consumers.MailEvent;
import com.mkwang.backend.modules.mail.publisher.MailStrategy;
import com.mkwang.backend.modules.mail.publisher.MailType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WarningMailStrategy — gửi email cảnh báo (PIN bị khóa, tài khoản bị lock...).
 * Publish message vào mailWarningQueue qua mailExchange.
 */
@Slf4j
@Component
public class WarningMailStrategy implements MailStrategy {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public WarningMailStrategy(
            RabbitTemplate rabbitTemplate,
            @Value("${spring.rabbitmq.mail.exchange}") String exchange,
            @Value("${spring.rabbitmq.mail.warning.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public MailType getType() {
        return MailType.WARNING;
    }

    @Override
    public void publish(String to, String subject, String content) {
        MailEvent message = new MailEvent(to, subject, content);
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.debug("[WarningMailStrategy] Published warning email to queue: to={}", to);
    }
}
