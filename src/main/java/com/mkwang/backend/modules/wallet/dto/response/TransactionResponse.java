package com.mkwang.backend.modules.wallet.dto.response;

import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {

    private Long id;
    private String transactionCode;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private ReferenceType referenceType;
    private Long referenceId;
    private String description;
    private LocalDateTime createdAt;
}
