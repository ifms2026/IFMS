package com.mkwang.backend.modules.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CategorySuggestionResponse {
    /** Null when the AI is not confident or the phase has no matching category. */
    private AiCategoryResponse category;
}
