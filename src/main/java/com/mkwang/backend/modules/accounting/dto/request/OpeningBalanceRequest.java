package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record OpeningBalanceRequest(@NotBlank String kind,@NotNull Long sourceId,
        @NotNull BigDecimal expectedSourceBalance,@NotNull BigDecimal expectedRecordedBalance,
        @NotBlank @Size(min=10,max=1000)String evidenceRef,@NotBlank String pin,
        @AssertTrue boolean historicalReviewComplete) {}
