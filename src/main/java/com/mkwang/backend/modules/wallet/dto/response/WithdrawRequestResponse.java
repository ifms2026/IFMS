package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.WithdrawStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for WithdrawRequest — returned by all withdraw endpoints.
 */
@Data
@Builder
public class WithdrawRequestResponse {

    private Long id;
    private String withdrawCode;
    private Long userId;
    private BigDecimal amount;

    private String userNote;
    private WithdrawStatus status;

    // Filled after processing
    private String accountantNote;
    private String failureReason;

    // Audit timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
