package com.mkwang.backend.modules.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyPinRequest {

    @NotBlank(message = "currentPin is required")
    private String currentPin;

    @NotBlank(message = "newPin is required")
    private String newPin;
}

