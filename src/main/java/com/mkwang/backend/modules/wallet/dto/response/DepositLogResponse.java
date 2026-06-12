package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.DepositStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class DepositLogResponse {
    Long id;
    String depositCode;
    BigDecimal amount;
    DepositStatus status;
    String paymentUrl;         // populated only on createDeposit — null for history entries
    String vnpTransactionNo;
    LocalDateTime paidAt;
    LocalDateTime createdAt;
}
