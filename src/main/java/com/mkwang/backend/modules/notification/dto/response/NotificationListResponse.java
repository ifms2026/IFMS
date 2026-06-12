package com.mkwang.backend.modules.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationListResponse {
    private List<NotificationDto> items;
    private long unreadCount;
    private long total;
    private int page;
    private int limit;
    private int totalPages;
}

