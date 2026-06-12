package com.mkwang.backend.modules.notification.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NotificationRabbitMQConfig — Exchange, Queue, DLX, Binding cho notification module.
 * <p>
 * Topology:
 * <pre>
 *   notificationExchange ──[notification.send]──► notificationQueue
 *                                                       ↓ (on NACK after retry)
 *                                                 notificationDLX ──► notificationDLQ
 * </pre>
 */
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationRabbitMQConfig {

    @Value("${spring.rabbitmq.notification.exchange}")
    String exchange;

    @Value("${spring.rabbitmq.notification.dlx}")
    String dlx;

    @Value("${spring.rabbitmq.notification.queue}")
    String queue;

    @Value("${spring.rabbitmq.notification.routing-key}")
    String routingKey;

    @Value("${spring.rabbitmq.notification.dlq}")
    String dlq;

    @Value("${spring.rabbitmq.notification.dlq-routing-key}")
    String dlqRoutingKey;

    // ── Main exchange + queue ─────────────────────────────────────

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(routingKey);
    }

    // ── DLX / DLQ ────────────────────────────────────────────────

    @Bean
    public DirectExchange notificationDLX() {
        return new DirectExchange(dlx);
    }

    @Bean
    public Queue notificationDLQ() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding notificationDLQBinding() {
        return BindingBuilder
                .bind(notificationDLQ())
                .to(notificationDLX())
                .with(dlqRoutingKey);
    }
}
