package com.mkwang.backend.modules.auth.dto.request;

public record VerifyOtpPasswordResetRequest(
        String email,
        String otp
) {
}
