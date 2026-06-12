package com.mkwang.backend.modules.profile.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinVerifyResponse {

    private boolean valid;

    /** Number of attempts remaining before PIN lockout. Null when valid=true. */
    private Integer attemptsRemaining;
}

