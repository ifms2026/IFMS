package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ReconcileCostCountersRequest(@NotNull Long projectId,@NotNull BigDecimal expectedCurrentCost,
    @NotNull BigDecimal expectedAvailableBudget,@NotNull BigDecimal expectedVerifiedCost,
    @NotBlank @Size(min=10,max=1000)String reason,@NotBlank String pin,@AssertTrue boolean evidenceReviewed) {}
