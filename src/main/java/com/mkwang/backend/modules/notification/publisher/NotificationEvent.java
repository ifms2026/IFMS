package com.mkwang.backend.modules.notification.publisher;

/**
 * NotificationEvent — payload được serialize qua RabbitMQ.
 *
 * @param userId    ID của người nhận notification
 * @param userEmail Email của người nhận (legacy field, không còn dùng cho SSE push)
 * @param type      NotificationType.name()
 * @param title     Tiêu đề ngắn gọn
 * @param message   Nội dung chi tiết
 * @param refId     ID của entity liên quan (Request, Payslip, Project, ...)
 * @param refType   Loại entity liên quan ("REQUEST", "PAYSLIP", "PROJECT", ...)
 */
public record NotificationEvent(
        Long userId,
        String userEmail,
        String type,
        String title,
        String message,
        Long refId,
        String refType
) {}
