package com.mkwang.backend.modules.ai.service.extractor;

import java.util.List;

/**
 * Reads a receipt image/PDF. Implementations are selected by {@code app.ai.provider}.
 * Implementations throw {@link com.mkwang.backend.common.exception.AiUnavailableException}
 * when the provider cannot be reached.
 */
public interface ReceiptExtractor {

    /** Value matched against {@code app.ai.provider}, e.g. "gemini" or "mock". */
    String provider();

    String model();

    /** False when required configuration (e.g. API key) is missing. */
    boolean isConfigured();

    /**
     * @param categories closed list the AI may choose from; empty when no phase is selected yet.
     *                   The returned categoryId is null or one of these ids.
     */
    ExtractedReceipt extract(byte[] content, String mimeType, String fileName, List<CategoryCandidate> categories);

    /** Text-only category pick for an already extracted receipt. Returns null when nothing fits. */
    Long suggestCategory(String expenseSummary, String vendorName, List<CategoryCandidate> categories);
}
