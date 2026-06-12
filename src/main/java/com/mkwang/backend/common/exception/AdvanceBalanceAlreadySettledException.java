package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

public class AdvanceBalanceAlreadySettledException extends BaseException {

    public AdvanceBalanceAlreadySettledException(Long advanceBalanceId) {
        super(
                "Advance balance is already fully settled" + (advanceBalanceId != null ? ": " + advanceBalanceId : ""),
                HttpStatus.BAD_REQUEST,
                "ADVANCE_BALANCE_ALREADY_SETTLED"
        );
    }
}

