package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedFileTypeException extends BaseException {

    public UnsupportedFileTypeException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
    }
}
