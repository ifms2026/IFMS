package com.mkwang.backend.modules.notification.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.notification.dto.response.NotificationListResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.notification.dto.response.NotificationDto;
import com.mkwang.backend.modules.notification.publisher.NotificationEvent;
import com.mkwang.backend.modules.notification.publisher.NotificationPublisher;
import com.mkwang.backend.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPublisher notificationPublisher;
    /**
     * GET /notifications
     * Lấy danh sách notifications của user hiện tại (mới nhất trước).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(
                principal.getUser().getId(), isRead, type, page, limit)));
    }

    /**
     * GET /notifications/unread-count
     * Số lượng notification chưa đọc — dùng cho badge icon trên UI.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal UserDetailsAdapter principal) {

        long count = notificationService.getUnreadCount(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * PATCH /notifications/{id}/read
     * Đánh dấu 1 notification đã đọc. Chỉ owner mới được phép.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {

        NotificationDto dto = notificationService.markAsRead(id, principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * PATCH /notifications/read-all
     * Đánh dấu tất cả notifications của user hiện tại là đã đọc.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserDetailsAdapter principal) {

        notificationService.markAllAsRead(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/test")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<String> test(
            @RequestBody NotificationEvent event
            ) {
        notificationPublisher.publish(event);
        return ResponseEntity.ok("Event published");
    }
}
