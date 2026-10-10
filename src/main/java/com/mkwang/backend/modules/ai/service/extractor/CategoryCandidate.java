package com.mkwang.backend.modules.ai.service.extractor;

/** One category the AI is allowed to pick — always taken from the phase's own list in the DB. */
public record CategoryCandidate(
        Long id,
        String name,
        String description
) {
}
