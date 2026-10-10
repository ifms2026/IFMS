package com.mkwang.backend.modules.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiStatusResponse {
    private boolean enabled;
    private String provider;
    private int maxCallsPerDay;
    private int remainingToday;
}
