package com.mkwang.backend.modules.request.dto.response;
import java.math.BigDecimal;
public record VerifiedCostSourceResponse(Long requestId,Long projectId,Long phaseId,Long categoryId,
        String type,String status,BigDecimal amount) {}
