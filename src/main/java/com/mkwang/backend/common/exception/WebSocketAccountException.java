package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Ném khi tài khoản bị vô hiệu hóa hoặc bị khóa trong quá trình WebSocket handshake.
 */
public class WebSocketAccountException extends BaseException {

    public WebSocketAccountException(String message) {
        super(message, HttpStatus.FORBIDDEN, "WS_ACCOUNT_RESTRICTED");
    }
}
