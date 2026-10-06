package com.mkwang.backend.modules.request.dto.response;

import java.math.BigDecimal;

public record AdvanceReturnResponse(
        Long advanceBalanceId,
        String transactionCode,
        BigDecimal returnedAmount,
        BigDecimal remainingAmount
) {}
