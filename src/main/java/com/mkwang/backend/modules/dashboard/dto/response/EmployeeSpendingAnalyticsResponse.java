package com.mkwang.backend.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class EmployeeSpendingAnalyticsResponse {

    private List<SpendingPoint> points;

    @Getter
    @Builder
    public static class SpendingPoint {
        private String label;
        private BigDecimal chiTieu;
        private BigDecimal tamUng;
    }
}
