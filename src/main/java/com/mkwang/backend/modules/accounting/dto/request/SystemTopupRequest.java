package com.mkwang.backend.modules.accounting.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SystemTopupRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    /** Bank transfer reference number from the real bank transaction. */
    @jakarta.validation.constraints.NotBlank(message = "Cần mã chứng từ/chuyển khoản ngân hàng")
    @jakarta.validation.constraints.Size(max = 100)
    private String paymentRef;

    private String description;
}
