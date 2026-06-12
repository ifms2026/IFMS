package com.mkwang.backend.modules.payment.exception;

import com.mkwang.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PaymentException extends BaseException {

    public PaymentException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "PAYMENT_ERROR");
    }

    public PaymentException(String message, HttpStatus status, String errorCode) {
        super(message, status, errorCode);
    }
}

