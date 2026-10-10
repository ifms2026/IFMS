package com.mkwang.backend.modules.ai.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.common.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads receipts with the Google Gemini API (generateContent) using structured JSON output.
 * <p>
 * The category is constrained by an enum in the response schema containing only the phase's
 * category ids plus "NONE", so the model cannot invent a category.
 */
@Slf4j
@Component
public class GeminiReceiptExtractor implements ReceiptExtractor {

    private static final String NO_CATEGORY = "NONE";

    private static final String EXTRACTION_PROMPT = """
            You read Vietnamese receipts and invoices: hoá đơn GTGT / hoá đơn điện tử, biên lai,
            bill nhà hàng, ride-hailing receipts (Grab, Be, Xanh SM), hotel folios, shop receipts.
            Extract these fields:
            - totalAmount: the FINAL amount paid (Tổng cộng / Tổng tiền thanh toán / Thành tiền sau thuế),
              including VAT, as a plain number. Vietnamese receipts use '.' as the thousands separator:
              "1.250.000" means 1250000.
            - invoiceDate: the invoice / payment date as yyyy-MM-dd. Vietnamese dates are written dd/MM/yyyy.
            - vendorName: the seller's name as printed.
            - vendorTaxCode: the seller's tax code (MST / Mã số thuế), digits and '-' only.
            - invoiceNumber: Số hoá đơn / Số HĐ / receipt or trip code.
            - vatAmount: VAT amount (Tiền thuế GTGT), or null.
            - currency: ISO currency code, normally VND.
            - expenseSummary: a short Vietnamese phrase (at most 12 words) saying what was paid for,
              e.g. "di chuyển bằng xe công nghệ", "ăn trưa tiếp khách", "mua văn phòng phẩm".
            - lineItems: up to 10 main purchased items with description, quantity and amount.
            Never guess. If a value is missing, cut off or unclear, return null for it.
            If the document is not a receipt or invoice, return null for every field.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    /** Primary model first, then fallbacks — tried in order when a model is overloaded / unavailable. */
    private final List<String> models;

    public GeminiReceiptExtractor(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ai.gemini.base-url}") String baseUrl,
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model}") String model,
            @Value("${app.ai.gemini.fallback-models:}") List<String> fallbackModels,
            @Value("${app.ai.timeout-ms}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.models = new ArrayList<>();
        this.models.add(model);
        fallbackModels.stream().map(String::trim)
                .filter(m -> !m.isEmpty() && !this.models.contains(m))
                .forEach(this.models::add);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000)));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ExtractedReceipt extract(byte[] content, String mimeType, String fileName, List<CategoryCandidate> categories) {
        String prompt = categories.isEmpty()
                ? EXTRACTION_PROMPT
                : EXTRACTION_PROMPT + categoryInstruction(categories);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", Base64.getEncoder().encodeToString(content))),
                                Map.of("text", prompt)))),
                "generationConfig", generationConfig(extractionSchema(categories)));

        GeminiResult result = generate(body);
        String json = result.text();
        try {
            JsonNode node = objectMapper.readTree(json);
            return new ExtractedReceipt(
                    decimal(node.get("totalAmount")),
                    text(node.get("invoiceDate")),
                    text(node.get("vendorName")),
                    text(node.get("vendorTaxCode")),
                    text(node.get("invoiceNumber")),
                    decimal(node.get("vatAmount")),
                    text(node.get("currency")),
                    text(node.get("expenseSummary")),
                    lineItems(node.get("lineItems")),
                    categoryId(text(node.get("categoryId"))),
                    json,
                    result.model());
        } catch (Exception e) {
            log.warn("[AI][Gemini] Could not parse extraction JSON: {}", e.getMessage());
            throw new AiUnavailableException("AI trả về dữ liệu không đọc được, vui lòng nhập tay");
        }
    }

    @Override
    public Long suggestCategory(String expenseSummary, String vendorName, List<CategoryCandidate> categories) {
        if (categories.isEmpty() || (isBlank(expenseSummary) && isBlank(vendorName))) {
            return null;
        }
        String prompt = "A Vietnamese receipt was read with these details:\n"
                + "- seller: " + nullToDash(vendorName) + "\n"
                + "- paid for: " + nullToDash(expenseSummary) + "\n"
                + categoryInstruction(categories);

        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("categoryId", categoryProperty(categories)),
                "required", List.of("categoryId"));

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig(schema));

        String json = generate(body).text();
        try {
            return categoryId(text(objectMapper.readTree(json).get("categoryId")));
        } catch (Exception e) {
            log.warn("[AI][Gemini] Could not parse category JSON: {}", e.getMessage());
            return null;
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────

    private record GeminiResult(String text, String model) {
    }

    /**
     * Calls the primary model, falling back to the next one when Google reports the model as
     * overloaded (503 / 5xx), rate limited (429) or not available for this key (404).
     */
    private GeminiResult generate(Map<String, Object> body) {
        if (!isConfigured()) {
            throw new AiUnavailableException("Chưa cấu hình GEMINI_API_KEY");
        }
        String lastError = null;
        for (String candidate : models) {
            try {
                return new GeminiResult(call(candidate, body), candidate);
            } catch (HttpClientErrorException.TooManyRequests | HttpClientErrorException.NotFound
                     | HttpServerErrorException e) {
                lastError = "HTTP " + e.getStatusCode().value();
                log.warn("[AI][Gemini] Model {} unavailable ({}): {} — trying next model",
                        candidate, lastError, truncate(e.getResponseBodyAsString()));
            } catch (RestClientResponseException e) {
                log.warn("[AI][Gemini] Model {} HTTP {}: {}", candidate, e.getStatusCode().value(),
                        truncate(e.getResponseBodyAsString()));
                throw new AiUnavailableException("Không gọi được dịch vụ AI (HTTP " + e.getStatusCode().value() + ")");
            } catch (ResourceAccessException e) {
                lastError = "timeout";
                log.warn("[AI][Gemini] Model {} network error / timeout: {} — trying next model", candidate, e.getMessage());
            }
        }
        log.warn("[AI][Gemini] All models failed {} (last: {})", models, lastError);
        throw new AiUnavailableException("Dịch vụ đọc chứng từ đang quá tải, vui lòng thử lại sau hoặc nhập tay");
    }

    private String call(String modelName, Map<String, Object> body) {
        JsonNode response = restClient.post()
                .uri("/models/{model}:generateContent", modelName)
                .header("x-goog-api-key", apiKey)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode textNode = response == null ? null
                : response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode == null || textNode.isMissingNode() || textNode.asText().isBlank()) {
            log.warn("[AI][Gemini] Empty response from {}: {}", modelName, truncate(String.valueOf(response)));
            throw new AiUnavailableException("AI không trả về kết quả, vui lòng nhập tay");
        }
        return textNode.asText();
    }

    // ── Request building ─────────────────────────────────────────

    private Map<String, Object> generationConfig(Map<String, Object> schema) {
        return Map.of(
                "temperature", 0,
                "responseMimeType", "application/json",
                "responseSchema", schema);
    }

    private Map<String, Object> extractionSchema(List<CategoryCandidate> categories) {
        Map<String, Object> lineItem = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "description", nullable("STRING"),
                        "quantity", nullable("NUMBER"),
                        "amount", nullable("NUMBER")));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("totalAmount", nullable("NUMBER"));
        properties.put("invoiceDate", nullable("STRING"));
        properties.put("vendorName", nullable("STRING"));
        properties.put("vendorTaxCode", nullable("STRING"));
        properties.put("invoiceNumber", nullable("STRING"));
        properties.put("vatAmount", nullable("NUMBER"));
        properties.put("currency", nullable("STRING"));
        properties.put("expenseSummary", nullable("STRING"));
        properties.put("lineItems", Map.of("type", "ARRAY", "nullable", true, "items", lineItem));
        if (!categories.isEmpty()) {
            properties.put("categoryId", categoryProperty(categories));
        }

        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", new ArrayList<>(properties.keySet()));
    }

    private Map<String, Object> categoryProperty(List<CategoryCandidate> categories) {
        List<String> allowed = new ArrayList<>();
        categories.forEach(c -> allowed.add(String.valueOf(c.id())));
        allowed.add(NO_CATEGORY);
        return Map.of("type", "STRING", "enum", allowed);
    }

    private String categoryInstruction(List<CategoryCandidate> categories) {
        StringBuilder sb = new StringBuilder()
                .append("\n- categoryId: pick the id of the ONE expense category below that best matches what was paid for,")
                .append(" using its name and description. If none clearly fits, answer ").append(NO_CATEGORY).append(".\n")
                .append("Categories:\n");
        categories.forEach(c -> sb.append("  id=").append(c.id())
                .append(" | ").append(c.name())
                .append(isBlank(c.description()) ? "" : " | " + c.description())
                .append('\n'));
        return sb.toString();
    }

    private Map<String, Object> nullable(String type) {
        return Map.of("type", type, "nullable", true);
    }

    // ── Response parsing ─────────────────────────────────────────

    private List<ExtractedReceipt.LineItem> lineItems(JsonNode node) {
        List<ExtractedReceipt.LineItem> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        for (JsonNode item : node) {
            String description = text(item.get("description"));
            BigDecimal amount = decimal(item.get("amount"));
            if (description == null && amount == null) {
                continue;
            }
            items.add(new ExtractedReceipt.LineItem(description, decimal(item.get("quantity")), amount));
        }
        return items;
    }

    private Long categoryId(String raw) {
        if (raw == null || NO_CATEGORY.equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    /** Accepts numbers or text such as "1.250.000 đ"; text keeps digits only (VND has no decimals). */
    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String digits = node.asText().replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : new BigDecimal(digits);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToDash(String s) {
        return isBlank(s) ? "-" : s;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }
}
