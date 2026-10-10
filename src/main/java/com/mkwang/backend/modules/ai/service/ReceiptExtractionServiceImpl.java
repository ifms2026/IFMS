package com.mkwang.backend.modules.ai.service;

import com.mkwang.backend.common.exception.AiQuotaExceededException;
import com.mkwang.backend.common.exception.AiUnavailableException;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.InternalSystemException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnsupportedFileTypeException;
import com.mkwang.backend.modules.ai.dto.request.ExtractionAttachmentLinkRequest;
import com.mkwang.backend.modules.ai.dto.response.AiCategoryResponse;
import com.mkwang.backend.modules.ai.dto.response.AiStatusResponse;
import com.mkwang.backend.modules.ai.dto.response.CategorySuggestionResponse;
import com.mkwang.backend.modules.ai.dto.response.ReceiptExtractionResponse;
import com.mkwang.backend.modules.ai.entity.DocumentExtraction;
import com.mkwang.backend.modules.ai.mapper.ReceiptExtractionMapper;
import com.mkwang.backend.modules.ai.repository.DocumentExtractionRepository;
import com.mkwang.backend.modules.ai.service.extractor.CategoryCandidate;
import com.mkwang.backend.modules.ai.service.extractor.ExtractedReceipt;
import com.mkwang.backend.modules.ai.service.extractor.ReceiptExtractor;
import com.mkwang.backend.modules.project.service.ProjectQueryService;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptExtractionServiceImpl implements ReceiptExtractionService {

    // Warning codes returned to the client / stored on the extraction
    static final String AMOUNT_UNREADABLE = "AMOUNT_UNREADABLE";
    static final String LINE_ITEMS_MISMATCH = "LINE_ITEMS_MISMATCH";
    static final String INVOICE_DATE_IN_FUTURE = "INVOICE_DATE_IN_FUTURE";
    static final String INVOICE_DATE_TOO_OLD = "INVOICE_DATE_TOO_OLD";
    static final String CURRENCY_NOT_VND = "CURRENCY_NOT_VND";
    static final String RECEIPT_AMOUNT_MISMATCH = "RECEIPT_AMOUNT_MISMATCH";
    static final String RECEIPT_REUSED = "RECEIPT_REUSED";

    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "webp", "image/webp", "pdf", "application/pdf");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter QUOTA_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String CACHE_KEY = "ai:receipt:%d:%s";
    private static final String QUOTA_KEY = "ai:quota:%d:%s";

    private final List<ReceiptExtractor> extractors;
    private final DocumentExtractionRepository documentExtractionRepository;
    private final ProjectQueryService projectQueryService;
    private final ReceiptExtractionMapper receiptExtractionMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.ai.enabled}")
    private boolean aiEnabled;

    @Value("${app.ai.provider}")
    private String provider;

    @Value("${app.ai.max-calls-per-user-per-day}")
    private int maxCallsPerDay;

    @Value("${app.ai.cache-ttl-hours}")
    private long cacheTtlHours;

    @Value("${app.ai.amount-mismatch-percent}")
    private BigDecimal amountMismatchPercent;

    @Value("${app.ai.max-invoice-age-days}")
    private long maxInvoiceAgeDays;

    @Value("${app.ai.max-amount}")
    private BigDecimal maxAmount;

    // ── Status ───────────────────────────────────────────────────

    @Override
    @PreAuthorize("hasAuthority('REQUEST_CREATE')")
    public AiStatusResponse getStatus(User currentUser) {
        ReceiptExtractor extractor = activeExtractor();
        boolean enabled = extractor != null;
        return AiStatusResponse.builder()
                .enabled(enabled)
                .provider(enabled ? extractor.provider() : null)
                .maxCallsPerDay(maxCallsPerDay)
                .remainingToday(enabled ? Math.max(0, maxCallsPerDay - usedToday(currentUser.getId())) : 0)
                .build();
    }

    // ── Extract ──────────────────────────────────────────────────

    // No @Transactional: the provider call can take seconds and must not hold a DB connection.
    @Override
    @PreAuthorize("hasAuthority('REQUEST_CREATE')")
    public ReceiptExtractionResponse extract(MultipartFile file, Long phaseId, User currentUser) {
        ReceiptExtractor extractor = requireExtractor();
        String mimeType = resolveMimeType(file);
        byte[] content = readContent(file);
        String fileHash = sha256(content);
        Long userId = currentUser.getId();
        List<CategoryCandidate> categories = loadCategories(currentUser, phaseId);

        // Same file again → reuse the stored extraction, only (re)pick the category for this phase
        DocumentExtraction cached = findCached(userId, fileHash);
        if (cached != null) {
            AiCategoryResponse category = categories.isEmpty() ? null : suggestQuietly(extractor, cached, categories);
            return receiptExtractionMapper.toResponse(cached, true, category);
        }

        consumeQuota(userId);
        ExtractedReceipt raw = extractor.extract(content, mimeType, file.getOriginalFilename(), categories);

        List<String> warnings = new ArrayList<>();
        BigDecimal total = sanitizeAmount(raw.totalAmount(), warnings);
        BigDecimal vat = raw.vatAmount() != null && raw.vatAmount().signum() >= 0
                && (total == null || raw.vatAmount().compareTo(total) < 0) ? scaleVnd(raw.vatAmount()) : null;
        LocalDate invoiceDate = parseDate(raw.invoiceDate(), warnings);
        String currency = raw.currency() == null ? null : raw.currency().trim().toUpperCase(Locale.ROOT);
        if (currency != null && !"VND".equals(currency)) {
            warnings.add(CURRENCY_NOT_VND);
        }
        checkLineItems(raw.lineItems(), total, vat, warnings);

        DocumentExtraction saved = documentExtractionRepository.save(DocumentExtraction.builder()
                .userId(userId)
                .fileHash(fileHash)
                .fileName(file.getOriginalFilename())
                .mimeType(mimeType)
                .provider(extractor.provider())
                .model(raw.model() != null ? raw.model() : extractor.model())
                .totalAmount(total)
                .invoiceDate(invoiceDate)
                .vendorName(trimToLength(raw.vendorName(), 255))
                .vendorTaxCode(trimToLength(raw.vendorTaxCode(), 30))
                .invoiceNumber(trimToLength(raw.invoiceNumber(), 100))
                .vatAmount(vat)
                .currency(trimToLength(currency, 10))
                .expenseSummary(raw.expenseSummary())
                .lineItems(receiptExtractionMapper.toJson(raw.lineItems()))
                .warnings(receiptExtractionMapper.toJson(warnings))
                .rawResponse(toJsonOrNull(raw.rawJson()))
                .build());

        cacheExtraction(userId, fileHash, saved.getId());
        log.info("[AI] Extracted receipt id={} user={} provider={} total={} warnings={}",
                saved.getId(), userId, extractor.provider(), total, warnings);

        return receiptExtractionMapper.toResponse(saved, false, toCategory(raw.categoryId(), categories));
    }

    // ── Category for a later-chosen phase ────────────────────────

    @Override
    @PreAuthorize("hasAuthority('REQUEST_CREATE')")
    public CategorySuggestionResponse suggestCategory(Long extractionId, Long phaseId, User currentUser) {
        ReceiptExtractor extractor = requireExtractor();
        DocumentExtraction extraction = documentExtractionRepository.findByIdAndUserId(extractionId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("DocumentExtraction", "id", extractionId));
        List<CategoryCandidate> categories = loadCategories(currentUser, phaseId);
        if (categories.isEmpty()) {
            return new CategorySuggestionResponse(null);
        }
        consumeQuota(currentUser.getId());
        Long categoryId = extractor.suggestCategory(extraction.getExpenseSummary(), extraction.getVendorName(), categories);
        return new CategorySuggestionResponse(toCategory(categoryId, categories));
    }

    // ── Link to request + cross-checks ───────────────────────────

    @Override
    public void linkToRequest(Long requesterId, Long requestId, BigDecimal requestAmount,
                              List<ExtractionAttachmentLinkRequest> links) {
        try {
            List<DocumentExtraction> linked = new ArrayList<>();
            for (ExtractionAttachmentLinkRequest link : links) {
                if (link.extractionId() == null) {
                    continue;
                }
                documentExtractionRepository.findByIdAndUserId(link.extractionId(), requesterId)
                        .ifPresentOrElse(e -> {
                            e.setRequestId(requestId);
                            e.setFileId(link.fileId());
                            linked.add(e);
                        }, () -> log.warn("[AI] Extraction {} not found for user {} — skipped", link.extractionId(), requesterId));
            }
            if (linked.isEmpty()) {
                return;
            }

            // Amount typed by the user vs total of the receipts (only when every receipt was read)
            boolean allRead = linked.stream().allMatch(e -> e.getTotalAmount() != null);
            BigDecimal receiptsTotal = linked.stream().map(DocumentExtraction::getTotalAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean amountMismatch = allRead && requestAmount != null && receiptsTotal.signum() > 0
                    && percentDiff(requestAmount, receiptsTotal).compareTo(amountMismatchPercent) > 0;

            for (DocumentExtraction e : linked) {
                List<String> warnings = receiptExtractionMapper.readStrings(e.getWarnings());
                if (amountMismatch) {
                    addOnce(warnings, RECEIPT_AMOUNT_MISMATCH);
                }
                if (e.getVendorTaxCode() != null && e.getInvoiceNumber() != null
                        && documentExtractionRepository.existsInvoiceOnOtherRequest(
                        e.getVendorTaxCode(), e.getInvoiceNumber(), requestId)) {
                    addOnce(warnings, RECEIPT_REUSED);
                }
                e.setWarnings(receiptExtractionMapper.toJson(warnings));
            }
            documentExtractionRepository.saveAll(linked);
            log.info("[AI] Linked {} extraction(s) to request {} (amountMismatch={})", linked.size(), requestId, amountMismatch);

        } catch (RuntimeException ex) {
            log.warn("[AI] Could not link extractions to request {}: {}", requestId, ex.getMessage());
        }
    }

    // ── Extractor selection ──────────────────────────────────────

    /** The configured extractor, or null when AI is disabled / misconfigured. */
    private ReceiptExtractor activeExtractor() {
        if (!aiEnabled) {
            return null;
        }
        return extractors.stream()
                .filter(e -> e.provider().equalsIgnoreCase(provider) && e.isConfigured())
                .findFirst()
                .orElse(null);
    }

    private ReceiptExtractor requireExtractor() {
        ReceiptExtractor extractor = activeExtractor();
        if (extractor == null) {
            throw new AiUnavailableException("Tính năng đọc chứng từ bằng AI đang tắt hoặc chưa được cấu hình");
        }
        return extractor;
    }

    private List<CategoryCandidate> loadCategories(User currentUser, Long phaseId) {
        if (phaseId == null) {
            return List.of();
        }
        return projectQueryService.getPhaseCategoryCandidates(currentUser, phaseId).stream()
                .map(c -> new CategoryCandidate(c.id(), c.name(), c.description()))
                .toList();
    }

    /** Only ids from the phase's own list are accepted — anything else becomes "no suggestion". */
    private AiCategoryResponse toCategory(Long categoryId, List<CategoryCandidate> categories) {
        if (categoryId == null) {
            return null;
        }
        return categories.stream()
                .filter(c -> c.id().equals(categoryId))
                .findFirst()
                .map(c -> new AiCategoryResponse(c.id(), c.name()))
                .orElse(null);
    }

    /** Category pick for a cached extraction: quota or provider errors just mean "no suggestion". */
    private AiCategoryResponse suggestQuietly(ReceiptExtractor extractor, DocumentExtraction e, List<CategoryCandidate> categories) {
        try {
            consumeQuota(e.getUserId());
            return toCategory(extractor.suggestCategory(e.getExpenseSummary(), e.getVendorName(), categories), categories);
        } catch (AiQuotaExceededException | AiUnavailableException ex) {
            log.info("[AI] Category suggestion skipped for cached extraction {}: {}", e.getId(), ex.getMessage());
            return null;
        }
    }

    // ── File handling ────────────────────────────────────────────

    private String resolveMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn file chứng từ");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (ALLOWED_MIME_TYPES.contains(contentType)) {
            return contentType;
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        String byExtension = MIME_BY_EXTENSION.get(extension);
        if (byExtension == null) {
            throw new UnsupportedFileTypeException("Chỉ hỗ trợ ảnh JPG, PNG, WEBP hoặc file PDF");
        }
        return byExtension;
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được file chứng từ");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new InternalSystemException("SHA-256 is not available");
        }
    }

    // ── Cache & quota (Redis) ────────────────────────────────────

    private DocumentExtraction findCached(Long userId, String fileHash) {
        String id = redisTemplate.opsForValue().get(CACHE_KEY.formatted(userId, fileHash));
        if (id == null) {
            return null;
        }
        try {
            return documentExtractionRepository.findByIdAndUserId(Long.parseLong(id), userId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cacheExtraction(Long userId, String fileHash, Long extractionId) {
        redisTemplate.opsForValue().set(CACHE_KEY.formatted(userId, fileHash), String.valueOf(extractionId),
                Duration.ofHours(cacheTtlHours));
    }

    private int usedToday(Long userId) {
        String value = redisTemplate.opsForValue().get(quotaKey(userId));
        return value == null ? 0 : Integer.parseInt(value);
    }

    private void consumeQuota(Long userId) {
        String key = quotaKey(userId);
        Long used = redisTemplate.opsForValue().increment(key);
        if (used != null && used == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        if (used != null && used > maxCallsPerDay) {
            redisTemplate.opsForValue().decrement(key);
            throw new AiQuotaExceededException(
                    "Bạn đã dùng hết " + maxCallsPerDay + " lượt đọc chứng từ hôm nay, vui lòng nhập tay");
        }
    }

    private static String quotaKey(Long userId) {
        return QUOTA_KEY.formatted(userId, LocalDate.now().format(QUOTA_DAY));
    }

    // ── Validation of AI output ──────────────────────────────────

    private BigDecimal sanitizeAmount(BigDecimal amount, List<String> warnings) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(maxAmount) > 0) {
            warnings.add(AMOUNT_UNREADABLE);
            return null;
        }
        return scaleVnd(amount);
    }

    private LocalDate parseDate(String raw, List<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException iso) {
            try {
                date = LocalDate.parse(raw.trim(), VN_DATE);
            } catch (DateTimeParseException vn) {
                return null;
            }
        }
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            warnings.add(INVOICE_DATE_IN_FUTURE);
        } else if (date.isBefore(today.minusDays(maxInvoiceAgeDays))) {
            warnings.add(INVOICE_DATE_TOO_OLD);
        }
        return date;
    }

    private void checkLineItems(List<ExtractedReceipt.LineItem> items, BigDecimal total, BigDecimal vat, List<String> warnings) {
        if (items == null || items.isEmpty() || total == null) {
            return;
        }
        BigDecimal itemsTotal = items.stream().map(ExtractedReceipt.LineItem::amount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemsTotal.signum() == 0) {
            return;
        }
        BigDecimal withVat = vat == null ? itemsTotal : itemsTotal.add(vat);
        if (percentDiff(itemsTotal, total).compareTo(amountMismatchPercent) > 0
                && percentDiff(withVat, total).compareTo(amountMismatchPercent) > 0) {
            warnings.add(LINE_ITEMS_MISMATCH);
        }
    }

    /** |a − b| / b × 100 */
    private static BigDecimal percentDiff(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().multiply(BigDecimal.valueOf(100)).divide(b, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleVnd(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private static String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static void addOnce(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    /** raw_response is a jsonb column — only store text that is a JSON object/array. */
    private static String toJsonOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.startsWith("{") || t.startsWith("[") ? t : null;
    }
}
