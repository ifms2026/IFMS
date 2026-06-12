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
public class TlApprovalDetailResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private String description;
    private RequesterDetail requester;
    private ProjectDetail project;
    private PhaseDetail phase;
    private Long categoryId;
    private String categoryName;
    private List<AttachmentResponse> attachments;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    public static class RequesterDetail {
        private Long id;
        private String fullName;
        private String avatar;
        private String employeeCode;
        private String jobTitle;
        private String email;
    }

    @Getter
    @Builder
    public static class ProjectDetail {
        private Long id;
        private String projectCode;
        private String name;
    }

    @Getter
    @Builder
    public static class PhaseDetail {
        private Long id;
        private String phaseCode;
        private String name;
        private BigDecimal budgetLimit;
        private BigDecimal currentSpent;
    }
}

