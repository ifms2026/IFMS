package com.mkwang.backend.modules.request.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AttachmentResponse {
    private Long fileId;
    private String fileName;
    private String cloudinaryPublicId;
    private String url;
    private String fileType;
    private Long size;
}

