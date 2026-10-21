package com.mkwang.backend.modules.request.dto.response;
import java.math.BigDecimal;
public record BudgetExposureResponse(Long phaseId,Long categoryId,BigDecimal committed,BigDecimal openAdvance) {}
