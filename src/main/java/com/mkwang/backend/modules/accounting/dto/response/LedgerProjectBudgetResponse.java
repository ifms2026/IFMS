package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record LedgerProjectBudgetResponse(
        Long projectId,
        String projectCode,
        String projectName,
        BigDecimal totalBudget,
        BigDecimal projectFundBalance,
        BigDecimal recognizedExpense,
        BigDecimal lockedForRequests,
        BigDecimal openAdvance,
        List<LedgerPhaseBudgetResponse> phases
) {}
