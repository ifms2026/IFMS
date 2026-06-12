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
public class AccountantDisbursementDetailResponse {

    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private BigDecimal approvedAmount;
    private String description;
    private String rejectReason;
    private LocalDateTime paidAt;
    private AccountantDisbursementSummaryResponse.RequesterBankSnippet requester;
    private AccountantDisbursementSummaryResponse.ProjectSnippet project;
    private PhaseDetail phase;
    private Long categoryId;
    private String categoryName;
    private List<AttachmentResponse> attachments;
    private List<RequestHistoryResponse> timeline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

