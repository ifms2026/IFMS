package com.mkwang.backend.modules.notification.scheduler;

import com.mkwang.backend.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NotificationScheduler — định kỳ dọn dẹp notifications đã đọc.
 * <p>
 * Cron schedule được cấu hình qua {@code app.notification.cleanup-cron} trong application.yml.
 * Default: {@code 0 0 0 * * *} — chạy lúc 00:00 mỗi ngày.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "${app.notification.cleanup-cron}")
    public void cleanupReadNotifications() {
        log.info("[NotificationScheduler] Starting read notification cleanup...");
        int deleted = notificationService.deleteReadNotifications();
        log.info("[NotificationScheduler] Cleanup complete — deleted {} read notifications", deleted);
    }
}
