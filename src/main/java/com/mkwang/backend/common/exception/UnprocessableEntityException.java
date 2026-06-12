package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends BaseException {

    public UnprocessableEntityException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY");
    }
}
