package com.mkwang.backend.modules.payment.dto.response;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentStatusResponse {
    private PaymentGateway gateway;
    private String transactionRef;
    private String status;
    private boolean successful;
    private String message;
}

