package com.mkwang.backend.modules.payment.exception;

import org.springframework.http.HttpStatus;

public class InvalidPaymentSignatureException extends PaymentException {

    public InvalidPaymentSignatureException() {
        super("Invalid payment signature", HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_SIGNATURE");
    }
}

