package com.mkwang.backend.modules.mail.consumers;

import com.mkwang.backend.common.exception.InternalSystemException;
import com.mkwang.backend.modules.mail.service.BrevoMailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * MailConsumer — nhận message từ RabbitMQ và gửi email qua Brevo.
 * <p>
 * Retry flow (áp dụng cho tất cả queue):
 * <ol>
 *   <li>Brevo call thất bại → Spring AMQP Retry tự thử lại (3 lần, exponential backoff 3s→9s)</li>
 *   <li>Hết 3 lần → NACK → RabbitMQ route sang DLQ tương ứng</li>
 *   <li>DLQ consumer log WARN để alert/monitor</li>
 * </ol>
 * concurrency="2-5": Spring AMQP tạo từ 2 đến 5 listener thread tự động.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MailConsumer {

    BrevoMailService mailService;

    // ── OnBoard ──────────────────────────────────────────────────

    @RabbitListener(queues = "${spring.rabbitmq.mail.onboard.queue}", concurrency = "2-5")
    public void consumeOnBoard(MailEvent email) {
        log.debug("[MailConsumer] Received onboard email for: {}", email.to());
        boolean success = mailService.sendOnBoard(email.to(), email.subject(), email.content());
        if (!success) {
            throw new InternalSystemException("Brevo send failed for onboard: " + email.to());
        }
    }

    @RabbitListener(queues = "${spring.rabbitmq.mail.onboard.dlq}")
    public void consumeOnBoardDLQ(Message rawMessage) {
        log.warn("[MailConsumer][DLQ] Onboard email FAILED after all retries. messageId={}, body={}",
                rawMessage.getMessageProperties().getMessageId(),
                new String(rawMessage.getBody()));
    }

    // ── Warning ──────────────────────────────────────────────────

    @RabbitListener(queues = "${spring.rabbitmq.mail.warning.queue}", concurrency = "2-5")
    public void consumeWarning(MailEvent email) {
        log.debug("[MailConsumer] Received warning email for: {}", email.to());
        boolean success = mailService.sendOnBoard(email.to(), email.subject(), email.content());
        if (!success) {
            throw new InternalSystemException("Brevo send failed for warning: " + email.to());
        }
    }

    @RabbitListener(queues = "${spring.rabbitmq.mail.warning.dlq}")
    public void consumeWarningDLQ(Message rawMessage) {
        log.warn("[MailConsumer][DLQ] Warning email FAILED after all retries. messageId={}, body={}",
                rawMessage.getMessageProperties().getMessageId(),
                new String(rawMessage.getBody()));
    }

    // ── Forget Password ──────────────────────────────────────────────────

    @RabbitListener(queues = "${spring.rabbitmq.mail.forget-password.queue}", concurrency = "2-5")
    public void consumeForgetPassword(MailEvent email) {
        log.debug("[MailConsumer] Received forget password email for: {}", email.to());
        boolean success = mailService.sendForgetPassword(email.to(), email.subject(), email.content());
        if (!success) {
            throw new InternalSystemException("Brevo send failed for forget password: " + email.to());
        }
    }

    @RabbitListener(queues = "${spring.rabbitmq.mail.forget-password.dlq}")
    public void consumeForgetPasswordDLQ(Message rawMessage) {
        log.warn("[MailConsumer][DLQ] Forget Password email FAILED after all retries. messageId={}, body={}",
                rawMessage.getMessageProperties().getMessageId(),
                new String(rawMessage.getBody()));
    }

}
