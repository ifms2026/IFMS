package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class DisburseResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private String transactionCode;
    private BigDecimal amount;
    private LocalDateTime disbursedAt;
}

