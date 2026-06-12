package com.mkwang.backend.modules.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationDto {

    private Long id;
    private String type;
    private String title;
    private String message;
    private Long refId;
    private String refType;
    private String referenceLink;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
