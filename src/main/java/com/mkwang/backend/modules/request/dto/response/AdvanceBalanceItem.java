package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Item in GET /requests/my-advance-balances — one unsettled AdvanceBalance. */
@Getter
@Builder
public class AdvanceBalanceItem {
    private Long id;
    private String requestCode;
    private BigDecimal originalAmount;
    private BigDecimal remainingAmount;
    private AdvanceBalanceStatus status;
}
