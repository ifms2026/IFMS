package com.mkwang.backend.modules.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DisburseRequest {

    @NotBlank
    private String pin;

    private String note;
}

