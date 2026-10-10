package com.mkwang.backend.modules.ai.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.modules.ai.dto.response.AiCategoryResponse;
import com.mkwang.backend.modules.ai.dto.response.ReceiptExtractionResponse;
import com.mkwang.backend.modules.ai.entity.DocumentExtraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptExtractionMapper {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final TypeReference<List<ReceiptExtractionResponse.LineItem>> LINE_ITEMS = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ReceiptExtractionResponse toResponse(DocumentExtraction e, boolean cached, AiCategoryResponse category) {
        List<ReceiptExtractionResponse.LineItem> items = readLineItems(e.getLineItems());

        ReceiptExtractionResponse.Fields fields = ReceiptExtractionResponse.Fields.builder()
                .totalAmount(e.getTotalAmount())
                .invoiceDate(e.getInvoiceDate())
                .vendorName(e.getVendorName())
                .vendorTaxCode(e.getVendorTaxCode())
                .invoiceNumber(e.getInvoiceNumber())
                .vatAmount(e.getVatAmount())
                .currency(e.getCurrency())
                .expenseSummary(e.getExpenseSummary())
                .lineItems(items)
                .build();

        ReceiptExtractionResponse.Suggestion suggestion = ReceiptExtractionResponse.Suggestion.builder()
                .title(buildTitle(e.getVendorName(), e.getExpenseSummary()))
                .descriptionDraft(buildDescription(e, items))
                .category(category)
                .build();

        return ReceiptExtractionResponse.builder()
                .extractionId(e.getId())
                .cached(cached)
                .fileName(e.getFileName())
                .fields(fields)
                .suggestion(suggestion)
                .warnings(readStrings(e.getWarnings()))
                .build();
    }

    // ── JSON columns ─────────────────────────────────────────────

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("[AI] Could not serialize value: {}", ex.getMessage());
            return null;
        }
    }

    public List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, STRINGS));
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }

    private List<ReceiptExtractionResponse.LineItem> readLineItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LINE_ITEMS);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    // ── Suggested text ───────────────────────────────────────────

    private String buildTitle(String vendor, String summary) {
        String title;
        if (summary != null && vendor != null) {
            title = capitalize(summary) + " – " + vendor;
        } else if (summary != null) {
            title = capitalize(summary);
        } else if (vendor != null) {
            title = "Thanh toán " + vendor;
        } else {
            return null;
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private String buildDescription(DocumentExtraction e, List<ReceiptExtractionResponse.LineItem> items) {
        List<String> lines = new ArrayList<>();
        if (e.getVendorName() != null) {
            lines.add("Chứng từ: " + e.getVendorName()
                    + (e.getVendorTaxCode() != null ? " · MST " + e.getVendorTaxCode() : ""));
        }
        if (e.getInvoiceNumber() != null) {
            lines.add("Số hoá đơn: " + e.getInvoiceNumber());
        }
        if (e.getInvoiceDate() != null) {
            lines.add("Ngày hoá đơn: " + VN_DATE.format(e.getInvoiceDate()));
        }
        for (ReceiptExtractionResponse.LineItem item : items) {
            StringBuilder line = new StringBuilder("- ").append(item.getDescription() != null ? item.getDescription() : "Mặt hàng");
            if (item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ONE) > 0) {
                line.append(" ×").append(item.getQuantity().stripTrailingZeros().toPlainString());
            }
            if (item.getAmount() != null) {
                line.append(": ").append(vnd(item.getAmount()));
            }
            lines.add(line.toString());
        }
        if (e.getVatAmount() != null) {
            lines.add("VAT: " + vnd(e.getVatAmount()));
        }
        if (e.getTotalAmount() != null) {
            lines.add("Tổng thanh toán: " + vnd(e.getTotalAmount()));
        }
        if (lines.isEmpty()) {
            return null;
        }
        String text = String.join("\n", lines);
        return text.length() > MAX_DESCRIPTION_LENGTH ? text.substring(0, MAX_DESCRIPTION_LENGTH) : text;
    }

    private static String vnd(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
