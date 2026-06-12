package com.mkwang.backend.modules.organization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentSummaryResponse {

    private Long id;
    private String name;
    private String code;
    private ManagerRef manager;
    private long employeeCount;
    private BigDecimal totalProjectQuota;
    private BigDecimal totalAvailableBalance;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerRef {
        private Long id;
        private String fullName;
    }
}
