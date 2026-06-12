package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientWalletBalanceException extends BaseException {

    public InsufficientWalletBalanceException(BigDecimal requestedAmount, BigDecimal availableAmount, String operation) {
        super(
                "Insufficient wallet balance for " + operation + ". Requested: " + requestedAmount + ", Available: " + availableAmount,
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_WALLET_BALANCE"
        );
    }
}

