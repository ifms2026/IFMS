package com.mkwang.backend.modules.spreadsheet.dto.response;
import java.util.Map;
public record ExcelRowResponse(int row, Map<String,String> values) {}
