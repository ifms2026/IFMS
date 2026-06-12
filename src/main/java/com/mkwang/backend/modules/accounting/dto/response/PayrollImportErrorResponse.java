package com.mkwang.backend.modules.accounting.dto.response;

/**
 * Field-level error entry returned in the errors[] array of PayrollImportResultResponse.
 */
public record PayrollImportErrorResponse(
        int row,
        String field,
        String message
) {}
