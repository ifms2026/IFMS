package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TlApproveResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private BigDecimal approvedAmount;
    private String comment;
}

