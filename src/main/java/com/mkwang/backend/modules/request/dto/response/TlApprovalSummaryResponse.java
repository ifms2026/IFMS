package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TlApprovalSummaryResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private String description;
    private RequesterSnippet requester;
    private ProjectSnippet project;
    private PhaseSnippet phase;
    private Long categoryId;
    private String categoryName;
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
    public static class ProjectSnippet {
        private Long id;
        private String projectCode;
        private String name;
    }

    @Getter
    @Builder
    public static class PhaseSnippet {
        private Long id;
        private String phaseCode;
        private String name;
        private BigDecimal budgetLimit;
        private BigDecimal currentSpent;
    }
}

