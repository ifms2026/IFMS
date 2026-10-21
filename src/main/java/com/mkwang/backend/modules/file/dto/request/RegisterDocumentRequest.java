package com.mkwang.backend.modules.file.dto.request;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class RegisterDocumentRequest extends FileStorageRequest {
    @NotNull private Long uploadVersion;
    @NotBlank private String uploadSignature;
}
