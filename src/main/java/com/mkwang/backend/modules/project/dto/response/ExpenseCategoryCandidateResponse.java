package com.mkwang.backend.modules.project.dto.response;

/** Category allocated to a phase, with its description — used by AI receipt scan to pick a category. */
public record ExpenseCategoryCandidateResponse(
        Long id,
        String name,
        String description
) {
}
