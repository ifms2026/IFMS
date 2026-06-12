package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AutoNettingResponse(
        Long periodId,
        String periodCode,
        BigDecimal totalAdvanceDeducted,
        List<AutoNettingSummaryEntryResponse> summary
) {}
