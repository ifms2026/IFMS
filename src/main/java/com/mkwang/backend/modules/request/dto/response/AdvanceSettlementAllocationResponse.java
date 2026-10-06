package com.mkwang.backend.modules.request.dto.response;

import java.math.BigDecimal;

public record AdvanceSettlementAllocationResponse(Long advanceBalanceId, BigDecimal amount) {}
