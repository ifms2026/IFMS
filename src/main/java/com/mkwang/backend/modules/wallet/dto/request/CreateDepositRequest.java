package com.mkwang.backend.modules.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateDepositRequest(
        @NotNull(message = "Số tiền nạp không được để trống")
        @DecimalMin(value = "10000", message = "Số tiền nạp tối thiểu là 10,000 VND")
        BigDecimal amount,

        String bankCode,
        String locale
) {
}
