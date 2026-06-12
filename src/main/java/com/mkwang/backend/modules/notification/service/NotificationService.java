package com.mkwang.backend.modules.notification.service;

import com.mkwang.backend.modules.notification.dto.response.NotificationDto;
import com.mkwang.backend.modules.notification.dto.response.NotificationListResponse;
import com.mkwang.backend.modules.notification.entity.NotificationType;

public interface NotificationService {

    /**
     * Publish một notification cho user — gọi bởi các service khác (RequestService, PayrollService, ...).
     * Không block business transaction: message được gửi vào RabbitMQ và xử lý async.
     *
     * @param userId    ID người nhận
     * @param userEmail Email người nhận (dùng làm WebSocket principal name)
     * @param type      Loại notification
     * @param title     Tiêu đề ngắn
     * @param message   Nội dung chi tiết
     * @param refId     ID entity liên quan (nullable)
     * @param refType   Loại entity liên quan, e.g. "REQUEST" (nullable)
     */
    void notify(Long userId, String userEmail, NotificationType type,
                String title, String message, Long refId, String refType);

    NotificationListResponse getNotifications(Long userId, Boolean isRead, String type, int page, int limit);

    /** Số lượng notification chưa đọc — dùng cho badge trên UI. */
    long getUnreadCount(Long userId);

    /** Đánh dấu 1 notification đã đọc. Kiểm tra ownership trước khi update. */
    NotificationDto markAsRead(Long notificationId, Long userId);

    /** Đánh dấu toàn bộ notifications của user là đã đọc. */
    void markAllAsRead(Long userId);

    /** Xóa toàn bộ notifications đã đọc — gọi bởi scheduled cleanup job. */
    int deleteReadNotifications();
}
