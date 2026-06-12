package com.mkwang.backend.modules.payment.dto.request;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
        @NotNull(message = "Gateway is required")
        PaymentGateway gateway,

        @NotBlank(message = "Transaction reference is required")
        @Size(max = 100, message = "Transaction reference must be at most 100 characters")
        String transactionRef,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}

