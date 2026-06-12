package com.mkwang.backend.modules.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String cloudinaryPublicId;

    @NotBlank
    private String url;

    private String fileType;
    private Long size;
}

