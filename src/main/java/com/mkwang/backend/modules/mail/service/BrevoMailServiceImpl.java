package com.mkwang.backend.modules.mail.service;

import org.thymeleaf.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Map;

/**
 * BrevoMailServiceImpl — Gửi email qua Brevo Transactional API v3.
 * <p>
 * POST https://api.brevo.com/v3/smtp/email
 * Header: api-key: <BREVO_API_KEY>
 * <p>
 * Method chạy đồng bộ — không cần @Async vì được gọi từ RabbitMQ listener thread
 * (đã là background thread, không block HTTP request).
 * Concurrency được điều khiển bởi concurrency="2-5" trong @RabbitListener.
 */
@Slf4j
@Service
public class BrevoMailServiceImpl implements BrevoMailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String fromEmail;
    private final String fromName;
    private final SpringTemplateEngine templateEngine;

    public BrevoMailServiceImpl(
            @Value("${application.mail.brevo-api-key}") String apiKey,
            @Value("${application.mail.from-email}") String fromEmail,
            @Value("${application.mail.from-name}") String fromName,
            SpringTemplateEngine templateEngine) {
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.templateEngine = templateEngine;
        this.restClient = RestClient.builder()
                .baseUrl(BREVO_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("api-key", apiKey)
                .build();
    }

    // ── Core send ────────────────────────────────────────────────

    @Override
    public boolean sendOnBoard(String to, String subject, String content) {
        try {
            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "htmlContent", content);

            var response = restClient.post()
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            log.info("[Brevo] HTTP={} | to={} | subject={}", response.getStatusCode(), to, subject);
            log.debug("[Brevo] Response body: {}", response.getBody());
            return true;

        } catch (Exception e) {
            log.error("[Brevo] Failed to send email: to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendForgetPassword(String to, String subject, String content) {
        try {
            Context context = new Context();
            context.setVariable("otpCode", content);
            String htmlContent = templateEngine.process("email/otp-email", context);

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "htmlContent", htmlContent);

            var response = restClient.post()
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            log.info("[Brevo] HTTP={} | to={} | subject={}", response.getStatusCode(), to, subject);
            log.debug("[Brevo] Response body: {}", response.getBody());
            return true;

        } catch (Exception e) {
            log.error("[Brevo] Failed to send email: to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            return false;
        }
    }


}
