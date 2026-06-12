package com.mkwang.backend.common.exception;

import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import org.springframework.http.HttpStatus;

public class InvalidTransactionStatusTransitionException extends BaseException {

    public InvalidTransactionStatusTransitionException(TransactionStatus currentStatus) {
        super(
                "Status transition is only allowed from PENDING. Current status: " + currentStatus,
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_STATUS_TRANSITION"
        );
    }
}

