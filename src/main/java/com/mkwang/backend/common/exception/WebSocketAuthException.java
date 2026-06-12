package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Ném khi JWT trong STOMP CONNECT frame thiếu, sai, hoặc đã hết hạn.
 */
public class WebSocketAuthException extends BaseException {

    public WebSocketAuthException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "WS_AUTH_FAILED");
    }

    public WebSocketAuthException(String message, Throwable cause) {
        super(message + (cause != null ? ": " + cause.getMessage() : ""),
                HttpStatus.UNAUTHORIZED, "WS_AUTH_FAILED");
    }
}
