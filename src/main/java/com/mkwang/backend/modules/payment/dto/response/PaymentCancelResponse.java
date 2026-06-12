package com.mkwang.backend.modules.payment.dto.response;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCancelResponse {
    private PaymentGateway gateway;
    private String transactionRef;
    private boolean cancelled;
    private String message;
}

