package com.mkwang.backend.modules.spreadsheet.dto.response;

import java.time.Instant;

public record ExcelExportLogResponse(Long id,String datasetCode,String scopeKey,String filterSnapshot,
        int rowCount,String fileSha256,Instant createdAt) {}
