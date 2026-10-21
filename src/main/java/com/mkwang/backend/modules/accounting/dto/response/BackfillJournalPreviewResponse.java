package com.mkwang.backend.modules.accounting.dto.response;
import java.math.BigDecimal;
import java.util.List;
public record BackfillJournalPreviewResponse(boolean eligible,List<OperationResponse> operations,List<String> errors) {
    public record OperationResponse(String event,Long sourceId,String code,BigDecimal amount,boolean alreadyRecorded) {}
}
