package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ManagerApprovalDetailResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private BigDecimal approvedAmount;
    private String description;
    private String rejectReason;
    private RequesterDetail requester;
    private ProjectDetail project;
    private DepartmentDetail department;
    private List<RequestHistoryResponse> timeline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class RequesterDetail {
        private Long id;
        private String fullName;
        private String avatar;
        private String employeeCode;
        private String jobTitle;
        private String email;
        private String departmentName;
    }

    @Getter
    @Builder
    public static class ProjectDetail {
        private Long id;
        private String projectCode;
        private String name;
        private BigDecimal availableBudget;
        private BigDecimal totalBudget;
    }

    @Getter
    @Builder
    public static class DepartmentDetail {
        private Long id;
        private String name;
        private BigDecimal totalAvailableBalance;
    }
}

