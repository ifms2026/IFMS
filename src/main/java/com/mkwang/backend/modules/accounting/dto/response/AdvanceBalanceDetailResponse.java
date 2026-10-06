package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdvanceBalanceDetailResponse(
        Long id,
        String requestCode,
        LocalDate disbursedDate,
        Long projectId,
        String projectName,
        String phaseName,
        String categoryName,
        BigDecimal originalAmount,
        BigDecimal reimbursedAmount,
        BigDecimal cashReturnedAmount,
        BigDecimal payrollOffsetAmount,
        BigDecimal legacyUnclassifiedAmount,
        BigDecimal remainingAmount,
        AdvanceBalanceStatus status,
        List<AdvanceActivityResponse> activities
) {}
