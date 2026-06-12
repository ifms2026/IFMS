package com.mkwang.backend.modules.mail.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MailRabbitMQConfig — khai báo Exchange, Queue, Binding cho mail service.
 * <p>
 * Topology:
 * <pre>
 *   mailExchange ──(mailOnboard)──► mailOnboardQueue ──► mailDLX ──► mailOnboardDLQ
 *                ──(mailWarning)──► mailWarningQueue ──► mailDLX ──► mailWarningDLQ
 * </pre>
 */
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MailRabbitMQConfig {

    // ── Shared exchange & DLX ────────────────────────────────────
    @Value("${spring.rabbitmq.mail.exchange}")
    String exchange;

    @Value("${spring.rabbitmq.mail.dlx}")
    String dlx;

    // ── OnBoard ──────────────────────────────────────────────────
    @Value("${spring.rabbitmq.mail.onboard.queue}")
    String onBoardQueue;

    @Value("${spring.rabbitmq.mail.onboard.routing-key}")
    String onBoardRoutingKey;

    @Value("${spring.rabbitmq.mail.onboard.dlq}")
    String onBoardDLQ;

    @Value("${spring.rabbitmq.mail.onboard.dlq-routing-key}")
    String onBoardDLQRoutingKey;

    // ── Warning ──────────────────────────────────────────────────
    @Value("${spring.rabbitmq.mail.warning.queue}")
    String warningQueue;

    @Value("${spring.rabbitmq.mail.warning.routing-key}")
    String warningRoutingKey;

    @Value("${spring.rabbitmq.mail.warning.dlq}")
    String warningDLQ;

    @Value("${spring.rabbitmq.mail.warning.dlq-routing-key}")
    String warningDLQRoutingKey;

// ── Forget Password ────────────────────────────────────────────
    @Value("${spring.rabbitmq.mail.forget-password.queue}")
    String forgetPasswordQueue;

    @Value("${spring.rabbitmq.mail.forget-password.routing-key}")
    String forgetPasswordRoutingKey;

    @Value("${spring.rabbitmq.mail.forget-password.dlq}")
    String forgetPasswordDLQ;

    @Value("${spring.rabbitmq.mail.forget-password.dlq-routing-key}")
    String forgetPasswordDLQRoutingKey;

    // ── Shared beans ─────────────────────────────────────────────

    @Bean
    public TopicExchange mailExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public DirectExchange mailDLX() {
        return new DirectExchange(dlx);
    }

    // ── OnBoard beans ────────────────────────────────────────────

    @Bean
    public Queue onBoardQueue() {
        return QueueBuilder.durable(onBoardQueue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", onBoardDLQRoutingKey)
                .build();
    }

    @Bean
    public Binding onBoardBinding() {
        return BindingBuilder.bind(onBoardQueue()).to(mailExchange()).with(onBoardRoutingKey);
    }

    @Bean
    public Queue onBoardDLQ() {
        return QueueBuilder.durable(onBoardDLQ).build();
    }

    @Bean
    public Binding onBoardDLQBinding() {
        return BindingBuilder.bind(onBoardDLQ()).to(mailDLX()).with(onBoardDLQRoutingKey);
    }

    // ── Warning beans ────────────────────────────────────────────

    @Bean
    public Queue warningQueue() {
        return QueueBuilder.durable(warningQueue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", warningDLQRoutingKey)
                .build();
    }

    @Bean
    public Binding warningBinding() {
        return BindingBuilder.bind(warningQueue()).to(mailExchange()).with(warningRoutingKey);
    }

    @Bean
    public Queue warningDLQ() {
        return QueueBuilder.durable(warningDLQ).build();
    }

    @Bean
    public Binding warningDLQBinding() {
        return BindingBuilder.bind(warningDLQ()).to(mailDLX()).with(warningDLQRoutingKey);
    }

//    ── Forget Password beans ────────────────────────────────────────────
    @Bean
    public Queue forgetPasswordQueue() {
        return QueueBuilder.durable(forgetPasswordQueue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", forgetPasswordDLQRoutingKey)
                .build();
    }

    @Bean
    public Binding forgetPasswordBinding() {
        return BindingBuilder.bind(forgetPasswordQueue()).to(mailExchange()).with(forgetPasswordRoutingKey);
    }

    @Bean
    public Queue forgetPasswordDLQ () {
        return QueueBuilder.durable(forgetPasswordDLQ).build();
    }

    @Bean
    public Binding forgetPasswordDLQBinding() {
        return BindingBuilder.bind(forgetPasswordDLQ()).to(mailDLX()).with(forgetPasswordDLQRoutingKey);
    }

}
