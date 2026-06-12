package com.mkwang.backend.modules.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileStorageRequest {

    @NotBlank(message = "File name is required")
    private String fileName;

    @NotBlank(message = "Cloudinary public ID is required")
    private String cloudinaryPublicId;

    @NotBlank(message = "URL is required")
    private String url;

    private String fileType;
    private Long size;
}