package com.mkwang.backend.modules.config.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateSettingsRequest(
        @NotEmpty(message = "Danh sách config không được rỗng")
        List<@Valid ConfigEntry> configs
) {
    public record ConfigEntry(
            @NotBlank(message = "Config key không được để trống")
            String key,

            @NotBlank(message = "Config value không được để trống")
            String value
    ) {}
}
