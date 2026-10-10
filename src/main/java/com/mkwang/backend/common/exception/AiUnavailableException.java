package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * AI feature is disabled, misconfigured, or the provider failed / timed out.
 * Uses 502 (not 503): the frontend treats any 503 as "system under maintenance".
 */
public class AiUnavailableException extends BaseException {

    public AiUnavailableException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE");
    }
}
