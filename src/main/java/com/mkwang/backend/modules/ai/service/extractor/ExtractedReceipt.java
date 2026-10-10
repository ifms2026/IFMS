package com.mkwang.backend.modules.ai.service.extractor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Raw values read by a {@link ReceiptExtractor}, before the service validates them.
 * invoiceDate is kept as text because providers may return dd/MM/yyyy despite instructions.
 */
public record ExtractedReceipt(
        BigDecimal totalAmount,
        String invoiceDate,
        String vendorName,
        String vendorTaxCode,
        String invoiceNumber,
        BigDecimal vatAmount,
        String currency,
        String expenseSummary,
        List<LineItem> lineItems,
        Long categoryId,
        String rawJson,
        /* Model that actually produced this result (may be a fallback model). */
        String model
) {
    public record LineItem(String description, BigDecimal quantity, BigDecimal amount) {
    }
}
