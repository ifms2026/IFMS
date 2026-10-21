package com.mkwang.backend.modules.accounting.dto.response;
import java.math.BigDecimal;
public record OpeningBalancePreviewResponse(String kind,Long sourceId,String accountCode,String label,
        BigDecimal sourceBalance,BigDecimal recordedBalance,BigDecimal difference,Long existingJournalId) {}
