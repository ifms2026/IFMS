package com.mkwang.backend.modules.payment.dto.response;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private PaymentGateway gateway;
    private String depositCode;
    private String transactionRef;
    private String paymentUrl;
    private String qrCode;
    private String status;
    private String message;
    private LocalDateTime expiredAt;
}

