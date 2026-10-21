package com.mkwang.backend.modules.wallet.dto.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record CorporateMovementResponse(Long id,String code,String paymentRef,LocalDateTime createdAt,
        String type,BigDecimal signedAmount) {}
