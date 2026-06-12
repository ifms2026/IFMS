package com.mkwang.backend.modules.dashboard.dto.response;

import com.mkwang.backend.modules.accounting.entity.PayrollStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AccountantDashboardResponse {

    private BigDecimal systemFundBalance;
    private long pendingDisbursementsCount;
    private BigDecimal monthlyInflow;
    private BigDecimal monthlyOutflow;
    private PayrollStatusSnapshot payrollStatus;

    @Getter
    @Builder
    public static class PayrollStatusSnapshot {
        private String latestPeriod;
        private PayrollStatus status;
    }
}
