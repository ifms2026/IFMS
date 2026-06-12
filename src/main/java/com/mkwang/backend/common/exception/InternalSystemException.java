package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

public class InternalSystemException extends BaseException {

    public InternalSystemException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SYSTEM_ERROR");
    }
}
