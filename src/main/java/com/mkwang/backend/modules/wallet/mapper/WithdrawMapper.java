package com.mkwang.backend.modules.wallet.mapper;

import com.mkwang.backend.modules.wallet.dto.response.WithdrawRequestResponse;
import com.mkwang.backend.modules.wallet.entity.WithdrawRequest;
import com.mkwang.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper for WithdrawRequest entity ↔ WithdrawRequestDto.
 */
@Component
@RequiredArgsConstructor
public class WithdrawMapper {

    private final UserRepository userRepository;

    public WithdrawRequestResponse toDto(WithdrawRequest req) {
        return WithdrawRequestResponse.builder()
                .id(req.getId())
                .withdrawCode(req.getWithdrawCode())
                .userId(req.getUserId())
                .requesterFullName(resolveRequesterName(req.getUserId()))
                .amount(req.getAmount())
                .creditAccount(req.getCreditAccount())
                .creditAccountName(req.getCreditAccountName())
                .creditBankCode(req.getCreditBankCode())
                .creditBankName(req.getCreditBankName())
                .userNote(req.getUserNote())
                .status(req.getStatus())
                .bankTransactionId(req.getBankTransactionId())
                .accountantNote(req.getAccountantNote())
                .executedBy(req.getExecutedBy())
                .executedAt(req.getExecutedAt())
                .transactionId(req.getTransactionId())
                .failureReason(req.getFailureReason())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }

    private String resolveRequesterName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> user.getFullName())
                .orElse(null);
    }
}
