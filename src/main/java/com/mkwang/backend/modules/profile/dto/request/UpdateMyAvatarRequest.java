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
public class UpdateMyAvatarRequest {

    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotBlank(message = "cloudinaryPublicId is required")
    private String cloudinaryPublicId;

    @NotBlank(message = "url is required")
    private String url;

    private String fileType;

    private Long size;
}

