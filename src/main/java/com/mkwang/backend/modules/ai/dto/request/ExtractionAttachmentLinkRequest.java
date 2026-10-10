package com.mkwang.backend.modules.ai.dto.request;

/** Links one AI extraction to the stored attachment file of a newly created request. */
public record ExtractionAttachmentLinkRequest(
        Long extractionId,
        Long fileId
) {
}
