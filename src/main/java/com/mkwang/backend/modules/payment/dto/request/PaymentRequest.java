package com.mkwang.backend.modules.payment.dto.request;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "Gateway is required")
        PaymentGateway gateway,

        @NotBlank(message = "Order code is required")
        @Size(max = 100, message = "Deposit code must be at most 100 characters")
        String depositCode,

        @NotBlank(message = "Order info is required")
        @Size(max = 255, message = "Deposit info must be at most 255 characters")
        String depositInfo,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1000", message = "Minimum amount is 1,000 VND")
        BigDecimal amount,

        @Size(max = 45, message = "IP address must be at most 45 characters")
        String ipAddress,

        @Size(max = 255, message = "Return URL must be at most 255 characters")
        String returnUrl,

        @Size(max = 20, message = "Bank code must be at most 20 characters")
        String bankCode,

        @Size(max = 10, message = "Locale must be at most 10 characters")
        String locale,

        @Positive(message = "Expire minutes must be positive")
        Long expireMinutes
) {
}

