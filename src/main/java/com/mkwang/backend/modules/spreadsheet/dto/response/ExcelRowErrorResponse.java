package com.mkwang.backend.modules.spreadsheet.dto.response;
public record ExcelRowErrorResponse(int row, String column, String message) {}
