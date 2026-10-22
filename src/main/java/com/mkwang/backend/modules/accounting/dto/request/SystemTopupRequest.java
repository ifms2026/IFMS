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

    @NotNull(message = "Cần chọn dòng sao kê ngân hàng đã ghi nhận")
    private Long bankEvidenceId;

    private String description;
}
