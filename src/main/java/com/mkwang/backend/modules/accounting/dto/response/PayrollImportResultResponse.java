package com.mkwang.backend.modules.accounting.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Top-level response for POST /accountant/payroll/:periodId/import
 */
public record PayrollImportResultResponse(
        Long periodId,
        String periodCode,
        String status,
        int totalRows,
        int successCount,
        int errorCount,
        List<PayrollImportEntryResponse> entries,
        List<PayrollImportErrorResponse> errors,
        BigDecimal totalNetPayroll
) {}
