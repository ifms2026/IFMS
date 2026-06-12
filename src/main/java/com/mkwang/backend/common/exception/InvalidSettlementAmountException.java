package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidSettlementAmountException extends BaseException {

    public InvalidSettlementAmountException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_SETTLEMENT_AMOUNT");
    }
}

