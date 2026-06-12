package com.mkwang.backend.modules.dashboard.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CfoDashboardResponse {

    private BigDecimal companyFundBalance;
    private long pendingApprovalsCount;
    private BigDecimal monthlyApprovedAmount;
    private long monthlyRejectedCount;
    private List<RecentApprovalItem> recentApprovals;

    @Getter
    @Builder
    public static class RecentApprovalItem {
        private Long id;
        private String requestCode;
        private String departmentName;
        private BigDecimal amount;
        private RequestStatus status;
        private LocalDateTime createdAt;
    }
}
