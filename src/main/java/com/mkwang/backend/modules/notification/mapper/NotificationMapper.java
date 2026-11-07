package com.mkwang.backend.modules.notification.mapper;

import com.mkwang.backend.modules.notification.dto.response.NotificationDto;
import com.mkwang.backend.modules.notification.entity.Notification;
import com.mkwang.backend.modules.notification.entity.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification notification) {
        boolean demoDataNotice = notification.getType() == NotificationType.SYSTEM
                && "DEMO_DATASET".equals(notification.getRefType());
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(demoDataNotice ? "Cập nhật hệ thống" : notification.getTitle())
                .message(demoDataNotice
                        ? "Hệ thống có thông tin mới. Truy cập các trang chức năng để xem nội dung liên quan."
                        : notification.getMessage())
                .refId(notification.getRefId())
                .refType(notification.getRefType())
                .referenceLink(notification.getReferenceLink())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
