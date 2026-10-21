package com.mkwang.backend.modules.spreadsheet.dto.response;
import java.util.List;
public record ExcelDatasetResponse(String code, String label, boolean importEnabled, boolean supportsDateFilter, String scopeLabel,
        int schemaVersion, List<ExcelColumnResponse> columns) {}
