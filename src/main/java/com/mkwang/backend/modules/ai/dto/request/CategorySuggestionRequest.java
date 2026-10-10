package com.mkwang.backend.modules.ai.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategorySuggestionRequest {

    @NotNull
    private Long phaseId;
}
