package com.mkwang.backend.modules.config.dto.response;

import com.mkwang.backend.modules.config.entity.SystemConfig;

import java.time.LocalDateTime;

/**
 * Response DTO cho SystemConfig.
 */
public record SystemConfigResponse(
        String key,
        String value,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SystemConfigResponse from(SystemConfig config) {
        return new SystemConfigResponse(
                config.getKey(),
                config.getValue(),
                config.getDescription(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
