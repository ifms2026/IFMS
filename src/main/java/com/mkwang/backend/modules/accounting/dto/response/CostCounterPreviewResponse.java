package com.mkwang.backend.modules.accounting.dto.response;
import com.mkwang.backend.modules.project.service.ProjectAccountingAdjustmentService.CostCounterResponse;
import java.math.BigDecimal;
import java.util.*;
public record CostCounterPreviewResponse(Long projectId,CostCounterResponse current,
        BigDecimal verifiedCost,Map<Long,BigDecimal> phaseCosts,Map<String,BigDecimal> categoryCosts,
        BigDecimal journalCost,int sourceCount,List<String> errors,boolean eligible) {}
