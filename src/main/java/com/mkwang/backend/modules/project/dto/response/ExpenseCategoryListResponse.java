package com.mkwang.backend.modules.project.dto.response;

import java.util.List;

public record ExpenseCategoryListResponse(
        List<ExpenseCategoryOptionResponse> items
) {
}

