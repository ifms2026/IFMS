package com.mkwang.backend.modules.mail.service;

import com.mkwang.backend.modules.mail.consumers.MailEvent;

/**
 * BrevoMailService — Gửi email qua Brevo Transactional API v3.
 * <p>
 * Các method được gọi đồng bộ từ RabbitMQ listener thread (đã là background thread).
 * Concurrency được quản lý bởi {@code concurrency} của {@code @RabbitListener}.
 * Return boolean: true = thành công, false = thất bại (logged).
 */
public interface BrevoMailService {

    boolean sendOnBoard(String to, String subject, String content);
    boolean sendForgetPassword(String to, String subject, String content);
}
