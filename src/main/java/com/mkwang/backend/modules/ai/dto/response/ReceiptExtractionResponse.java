package com.mkwang.backend.modules.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of POST /ai/receipts/extract. Every field may be null when the AI could not read it —
 * the frontend only fills empty form fields and never overwrites what the user typed.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ReceiptExtractionResponse {

    private Long extractionId;
    private boolean cached;
    private String fileName;
    private Fields fields;
    private Suggestion suggestion;
    private List<String> warnings;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Fields {
        private BigDecimal totalAmount;
        private LocalDate invoiceDate;
        private String vendorName;
        private String vendorTaxCode;
        private String invoiceNumber;
        private BigDecimal vatAmount;
        private String currency;
        private String expenseSummary;
        private List<LineItem> lineItems;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class LineItem {
        private String description;
        private BigDecimal quantity;
        private BigDecimal amount;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Suggestion {
        private String title;
        private String descriptionDraft;
        private AiCategoryResponse category;
    }
}
