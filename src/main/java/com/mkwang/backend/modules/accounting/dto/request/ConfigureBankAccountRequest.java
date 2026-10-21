package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
public record ConfigureBankAccountRequest(@NotBlank @Size(max=30)String account,
        @NotBlank @Size(max=100)String bankName,@NotBlank String purpose,@NotBlank @Size(max=1000)String evidenceRef) {}
