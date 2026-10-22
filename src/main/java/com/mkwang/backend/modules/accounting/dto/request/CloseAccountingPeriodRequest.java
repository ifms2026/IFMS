package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CloseAccountingPeriodRequest(
        @NotBlank @Size(max=1000) String reason,
        boolean reviewAcknowledged) {}
