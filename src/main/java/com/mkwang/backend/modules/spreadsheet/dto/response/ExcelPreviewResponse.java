package com.mkwang.backend.modules.spreadsheet.dto.response;
import java.util.List;
public record ExcelPreviewResponse(String batchId, String dataset, String state, int totalRows,
        int validRows, int errorRows, List<ExcelRowResponse> rows, List<ExcelRowErrorResponse> errors, List<String> results) {}
