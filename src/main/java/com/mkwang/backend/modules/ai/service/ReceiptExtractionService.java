package com.mkwang.backend.modules.ai.service;

import com.mkwang.backend.modules.ai.dto.request.ExtractionAttachmentLinkRequest;
import com.mkwang.backend.modules.ai.dto.response.AiStatusResponse;
import com.mkwang.backend.modules.ai.dto.response.CategorySuggestionResponse;
import com.mkwang.backend.modules.ai.dto.response.ReceiptExtractionResponse;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public interface ReceiptExtractionService {

    AiStatusResponse getStatus(User currentUser);

    /**
     * Reads one receipt file. When phaseId is given, also picks one of that phase's existing
     * categories (or none). Same file uploaded again by the same user is served from cache.
     */
    ReceiptExtractionResponse extract(MultipartFile file, Long phaseId, User currentUser);

    /** Picks a category for an earlier extraction once the user has chosen a phase. Text-only call. */
    CategorySuggestionResponse suggestCategory(Long extractionId, Long phaseId, User currentUser);

    /**
     * Called while a request is being created: links the user's extractions to the request and its
     * stored files, then flags amount mismatch and invoice reuse. Never throws — a failure here
     * must not block request creation.
     */
    void linkToRequest(Long requesterId, Long requestId, BigDecimal requestAmount,
                       List<ExtractionAttachmentLinkRequest> links);
}
