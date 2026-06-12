package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CfoApprovalSummaryResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private String description;
    private RequesterSnippet requester;
    private DepartmentSnippet department;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    public static class RequesterSnippet {
        private Long id;
        private String fullName;
        private String avatar;
        private String employeeCode;
        private String jobTitle;
        private String email;
    }

    @Getter
    @Builder
    public static class DepartmentSnippet {
        private Long id;
        private String name;
        private String code;
        private BigDecimal totalAvailableBalance;
    }
}
