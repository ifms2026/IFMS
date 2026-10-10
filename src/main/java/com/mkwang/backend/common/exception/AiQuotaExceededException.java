package com.mkwang.backend.common.exception;

import org.springframework.http.HttpStatus;

/** The user has used up their daily AI call allowance. */
public class AiQuotaExceededException extends BaseException {

    public AiQuotaExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "AI_QUOTA_EXCEEDED");
    }
}
