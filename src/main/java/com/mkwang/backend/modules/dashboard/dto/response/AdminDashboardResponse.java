package com.mkwang.backend.modules.dashboard.dto.response;

import com.mkwang.backend.modules.audit.entity.AuditAction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminDashboardResponse {

    private long totalUsers;
    private long totalDepartments;
    private BigDecimal totalWalletBalance;
    private List<RecentAuditEvent> recentAuditEvents;

    @Getter
    @Builder
    public static class RecentAuditEvent {
        private Long id;
        private String actorName;
        private AuditAction action;
        private String entityName;
        private LocalDateTime createdAt;
    }
}
