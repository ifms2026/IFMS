package com.mkwang.backend.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ManagerDashboardResponse {

    private DepartmentBudget departmentBudget;
    private ProjectStatusSummary projectStatusSummary;
    private long pendingApprovalsCount;
    private TeamDebtSummary teamDebtSummary;

    @Getter
    @Builder
    public static class DepartmentBudget {
        private BigDecimal totalProjectQuota;
        private BigDecimal totalAvailableBalance;
        private BigDecimal totalSpent;
    }

    @Getter
    @Builder
    public static class ProjectStatusSummary {
        private long active;
        private long planning;
        private long paused;
        private long closed;
    }

    @Getter
    @Builder
    public static class TeamDebtSummary {
        private BigDecimal totalDebt;
        private long employeesWithDebt;
    }
}
