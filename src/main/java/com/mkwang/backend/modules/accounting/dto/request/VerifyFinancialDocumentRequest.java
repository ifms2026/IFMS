package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record VerifyFinancialDocumentRequest(
        @NotNull Long fileId, @NotBlank @Size(max=100) String issuerCode,
        @NotBlank @Size(max=100) String documentNumber, @NotNull @PastOrPresent LocalDate documentDate,
        @NotNull @DecimalMin("0.01") @Digits(integer=15,fraction=2) BigDecimal totalAmount,
        @NotNull @DecimalMin("0.01") @Digits(integer=15,fraction=2) BigDecimal allocatedAmount) {}
