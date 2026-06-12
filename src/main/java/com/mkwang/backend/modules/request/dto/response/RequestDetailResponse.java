package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestType;
import com.mkwang.backend.modules.request.entity.RequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RequestDetailResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private BigDecimal approvedAmount;
    private String description;
    private String rejectReason;
    private LocalDateTime paidAt;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long phaseId;
    private String phaseCode;
    private String phaseName;
    private Long categoryId;
    private String categoryName;
    private Long advanceBalanceId;
    private Long requesterId;
    private String requesterName;
    private List<AttachmentResponse> attachments;
    private List<RequestHistoryResponse> timeline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

