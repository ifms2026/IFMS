package com.mkwang.backend.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class AdminAnalyticsResponse {

    private List<DeptSpendingItem> deptSpending;
    private List<TopDebtorItem> topDebtors;

    @Getter
    @Builder
    public static class DeptSpendingItem {
        private Long deptId;
        private String deptName;
        private BigDecimal spent;
    }

    @Getter
    @Builder
    public static class TopDebtorItem {
        private Long userId;
        private String fullName;
        private String deptName;
        private BigDecimal outstandingAmount;
        private long daysSinceDisbursement;
    }
}
