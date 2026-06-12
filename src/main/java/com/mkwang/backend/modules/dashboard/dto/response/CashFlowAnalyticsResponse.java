package com.mkwang.backend.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CashFlowAnalyticsResponse {

    private String period;
    private List<CashFlowPoint> points;
    private BigDecimal totalInflow;
    private BigDecimal totalOutflow;

    @Getter
    @Builder
    public static class CashFlowPoint {
        private String label;
        private BigDecimal inflow;
        private BigDecimal outflow;
    }
}
