package com.mkwang.backend.modules.config.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO để cập nhật hoặc tạo mới một SystemConfig entry.
 */
public record SystemConfigRequest(
        @NotBlank(message = "Giá trị config không được để trống")
        @Size(max = 1000, message = "Giá trị config tối đa 1000 ký tự")
        String value,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description
) {}
