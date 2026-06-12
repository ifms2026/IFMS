package com.mkwang.backend.modules.accounting.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateBankStatementRequest {

    @NotNull(message = "External bank balance is required")
    @DecimalMin(value = "0.00", message = "External bank balance cannot be negative")
    private BigDecimal externalBankBalance;

    @NotNull(message = "Statement date is required")
    private LocalDate lastStatementDate;
}
