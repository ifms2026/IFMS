package com.mkwang.backend.modules.ai.dto.response;

/** An existing expense category picked by the AI from the phase's own list. */
public record AiCategoryResponse(
        Long id,
        String name
) {
}
