package com.mkwang.backend.modules.payment.dto.response;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class PaymentCallbackResult {
    private PaymentGateway gateway;
    private boolean success;
    private String message;
    private String orderCode;
    private String transactionRef;
    private String gatewayTransactionId;
    private String responseCode;
    private String transactionStatus;
    private BigDecimal amount;
    private LocalDateTime paidAt;
    private Map<String, String> rawParams;
}

