package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ReplacePayslipRequest(@NotNull @DecimalMin("0.01") BigDecimal baseSalary,
        @NotNull @DecimalMin("0") BigDecimal bonus,@NotNull @DecimalMin("0") BigDecimal allowance,
        @NotNull @DecimalMin("0") BigDecimal deduction,@NotBlank String reason,@NotBlank String evidenceRef,@NotBlank String pin) {}
