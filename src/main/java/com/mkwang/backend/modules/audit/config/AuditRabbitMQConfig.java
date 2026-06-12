package com.mkwang.backend.modules.audit.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AuditRabbitMQConfig — khai báo Exchange, Queue, Binding cho audit module.
 * <p>
 * Topology:
 * <pre>
 *   auditExchange ──(audit)──► auditQueue ──(on reject)──► auditDLX ──► auditDLQ
 * </pre>
 * main queue có x-dead-letter-exchange → message bị NACK sau retry sẽ route sang DLQ.
 */
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditRabbitMQConfig {

    @Value("${spring.rabbitmq.audit.exchange}")
    String exchange;

    @Value("${spring.rabbitmq.audit.dlx}")
    String dlx;

    @Value("${spring.rabbitmq.audit.queue}")
    String auditQueue;

    @Value("${spring.rabbitmq.audit.routing-key}")
    String routingKey;

    @Value("${spring.rabbitmq.audit.dlq}")
    String auditDLQ;

    @Value("${spring.rabbitmq.audit.dlq-routing-key}")
    String dlqRoutingKey;

    // ── Main ─────────────────────────────────────────────────────

    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(auditQueue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder
                .bind(auditQueue())
                .to(auditExchange())
                .with(routingKey);
    }

    // ── DLX / DLQ ────────────────────────────────────────────────

    @Bean
    public DirectExchange auditDLX() {
        return new DirectExchange(dlx);
    }

    @Bean
    public Queue auditDLQ() {
        return QueueBuilder.durable(auditDLQ).build();
    }

    @Bean
    public Binding auditDLQBinding() {
        return BindingBuilder
                .bind(auditDLQ())
                .to(auditDLX())
                .with(dlqRoutingKey);
    }

    // ── Async Publish Executor ────────────────────────────────────

    /**
     * Dedicated thread pool cho AuditPublisher.publishAsync().
     * Tách việc gọi RabbitMQ ra khỏi Hibernate commit thread (= request thread).
     * - corePoolSize = 2: đủ cho baseline traffic
     * - maxPoolSize = 5: burst capacity
     * - queueCapacity = 500: buffer khi burst đột ngột
     */
    @Bean(name = "auditPublishExecutor")
    public ThreadPoolTaskExecutor auditPublishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-pub-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
