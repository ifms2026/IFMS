package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
public record MatchBankStatementRequest(@NotNull Long transactionId,@NotBlank @Size(max=1000)String reason) {}
