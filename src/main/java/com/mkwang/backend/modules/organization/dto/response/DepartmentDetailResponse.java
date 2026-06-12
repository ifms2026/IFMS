package com.mkwang.backend.modules.organization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDetailResponse {

    private Long id;
    private String name;
    private String code;
    private ManagerRef manager;
    private BigDecimal totalProjectQuota;
    private BigDecimal totalAvailableBalance;
    private List<MemberResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerRef {
        private Long id;
        private String fullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private Long id;
        private String fullName;
        private String employeeCode;
        private String email;
        private String jobTitle;
        private String avatar;
        private String status;
    }
}
