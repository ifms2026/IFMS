package com.mkwang.backend.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQBaseConfig — cấu hình chung cho toàn bộ RabbitMQ trong hệ thống.
 * <p>
 * Bao gồm:
 * <ul>
 *   <li>Jackson converter dùng chung cho producer và consumer</li>
 *   <li>RabbitTemplate với Jackson converter (producer)</li>
 *   <li>Default listener container factory với Retry + DLQ (consumer)</li>
 * </ul>
 * <p>
 * Retry policy (áp dụng cho TẤT CẢ {@code @RabbitListener}):
 * <ul>
 *   <li>Attempt 1: ngay lập tức</li>
 *   <li>Attempt 2: sau 3 giây</li>
 *   <li>Attempt 3: sau 9 giây (multiplier 3x)</li>
 *   <li>Hết retry → NACK → RabbitMQ route sang DLQ (nếu queue có x-dead-letter-exchange)</li>
 * </ul>
 */
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RabbitMQBaseConfig {

    // ── Converter (dùng chung producer + consumer) ───────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ── Producer ─────────────────────────────────────────────────

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ── Consumer: Default Listener Factory ───────────────────────

    /**
     * Override default factory (bean name "rabbitListenerContainerFactory").
     * <p>
     * Mọi {@code @RabbitListener} KHÔNG chỉ định {@code containerFactory} sẽ dùng
     * factory này — tự động có Jackson converter + Retry + DLQ mà không cần config thêm.
     * <p>
     * Nếu một listener cần policy khác (ví dụ retry nhiều hơn), tạo factory riêng
     * và chỉ định {@code containerFactory = "customFactory"} trong {@code @RabbitListener}.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAdviceChain(defaultRetryInterceptor());
        return factory;
    }

    /**
     * Retry interceptor mặc định — ExponentialBackOff 3 lần.
     * Sau khi hết retry → {@link RejectAndDontRequeueRecoverer} gửi NACK → DLQ.
     */
    @Bean
    public RetryOperationsInterceptor defaultRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(
                        3_000L, // initialInterval: 3s
                        3.0,    // multiplier: 3x → 3s, 9s
                        30_000L // maxInterval: 30s
                )
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
