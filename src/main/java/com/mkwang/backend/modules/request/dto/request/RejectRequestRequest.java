package com.mkwang.backend.modules.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequestRequest {

    @NotBlank
    @Size(min = 10, message = "Lý do từ chối cần ít nhất 10 ký tự")
    private String reason;
}

