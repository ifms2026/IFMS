package com.mkwang.backend.modules.wallet.mapper;

import com.mkwang.backend.modules.wallet.dto.response.WithdrawRequestResponse;
import com.mkwang.backend.modules.wallet.entity.WithdrawRequest;
import org.springframework.stereotype.Component;

/**
 * Mapper for WithdrawRequest entity ↔ WithdrawRequestDto.
 */
@Component
public class WithdrawMapper {

    public WithdrawRequestResponse toDto(WithdrawRequest req) {
        return WithdrawRequestResponse.builder()
                .id(req.getId())
                .withdrawCode(req.getWithdrawCode())
                .userId(req.getUserId())
                .amount(req.getAmount())
                .userNote(req.getUserNote())
                .status(req.getStatus())
                .accountantNote(req.getAccountantNote())
                .failureReason(req.getFailureReason())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }
}
