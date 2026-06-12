package com.mkwang.backend.modules.payment.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedPaymentGatewayException extends PaymentException {

    public UnsupportedPaymentGatewayException(String gateway) {
        super("Unsupported payment gateway: " + gateway, HttpStatus.BAD_REQUEST, "UNSUPPORTED_PAYMENT_GATEWAY");
    }
}

