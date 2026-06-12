package com.mkwang.backend.modules.file.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadSignatureResponse {
    private String signature;
    private long timestamp;
    private String apiKey;
    private String cloudName;
    private String folder;
}