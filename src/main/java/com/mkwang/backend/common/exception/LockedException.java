package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

public class LockedException extends BaseException {

    public LockedException(String message) {
        super(message, HttpStatus.LOCKED, "RESOURCE_LOCKED");
    }

    public LockedException() {
        this("Resource is temporarily locked");
    }
}

