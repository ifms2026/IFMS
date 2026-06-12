package com.mkwang.backend.modules.notification.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.modules.notification.dto.response.NotificationDto;
import com.mkwang.backend.modules.notification.dto.response.NotificationListResponse;
import com.mkwang.backend.modules.notification.entity.Notification;
import com.mkwang.backend.modules.notification.entity.NotificationType;
import com.mkwang.backend.modules.notification.mapper.NotificationMapper;
import com.mkwang.backend.modules.notification.publisher.NotificationEvent;
import com.mkwang.backend.modules.notification.publisher.NotificationPublisher;
import com.mkwang.backend.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPublisher  notificationPublisher;
    private final NotificationMapper     notificationMapper;

    // ── Internal publish (gọi bởi các service khác) ─────────────

    @Override
    public void notify(Long userId, String userEmail, NotificationType type,
                       String title, String message, Long refId, String refType) {
        notificationPublisher.publish(
                new NotificationEvent(userId, userEmail, type.name(), title, message, refId, refType)
        );
    }

    // ── REST ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public NotificationListResponse getNotifications(Long userId, Boolean isRead, String type, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(limit, 1);
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);

        NotificationType parsedType = parseType(type);
        Page<Notification> resultPage = getFilteredPage(userId, isRead, parsedType, pageable);

        return NotificationListResponse.builder()
                .items(resultPage.map(notificationMapper::toDto).getContent())
                .unreadCount(notificationRepository.countByUserIdAndIsReadFalse(userId))
                .total(resultPage.getTotalElements())
                .page(safePage)
                .limit(safeLimit)
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    private Page<Notification> getFilteredPage(Long userId, Boolean isRead, NotificationType type, Pageable pageable) {
        if (type == null) {
            if (Boolean.TRUE.equals(isRead)) {
                return notificationRepository.findByUserIdAndIsReadTrueOrderByCreatedAtDesc(userId, pageable);
            }
            if (Boolean.FALSE.equals(isRead)) {
                return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable);
            }
            return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        if (Boolean.TRUE.equals(isRead)) {
            return notificationRepository.findByUserIdAndTypeAndIsReadTrueOrderByCreatedAtDesc(userId, type, pageable);
        }
        if (Boolean.FALSE.equals(isRead)) {
            return notificationRepository.findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(userId, type, pageable);
        }
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
    }

    private NotificationType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return NotificationType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid notification type: " + type);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public NotificationDto markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Cannot mark another user's notification as read");
        }

        notification.markAsRead();
        return notificationMapper.toDto(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    @Transactional
    public int deleteReadNotifications() {
        return notificationRepository.deleteAllRead();
    }
}
