package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Item in GET /requests/my-advance-balances — one unsettled AdvanceBalance. */
@Getter
@Builder
public class AdvanceBalanceItem {
    private Long id;
    private String requestCode;
    private String projectName;
    private LocalDate disbursedDate;
    private BigDecimal originalAmount;
    private BigDecimal remainingAmount;
    private AdvanceBalanceStatus status;

    // Project / phase / category of the original advance — pre-filled on the reimbursement form
    private Long projectId;
    private Long phaseId;
    private String phaseName;
    private Long categoryId;
    private String categoryName;
}
