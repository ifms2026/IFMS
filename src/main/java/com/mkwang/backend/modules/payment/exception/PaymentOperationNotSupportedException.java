package com.mkwang.backend.modules.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentOperationNotSupportedException extends PaymentException {

    public PaymentOperationNotSupportedException(String operation, String gateway) {
        super(operation + " is not supported by gateway: " + gateway,
                HttpStatus.BAD_REQUEST,
                "PAYMENT_OPERATION_NOT_SUPPORTED");
    }
}

