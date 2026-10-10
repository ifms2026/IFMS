package com.mkwang.backend.modules.ai.service.extractor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline stand-in for demos and frontend work ({@code app.ai.provider=mock}).
 * Picks a sample receipt from keywords in the file name and matches categories by keyword,
 * so the whole flow (including category pick) works without network or API key.
 */
@Component
public class MockReceiptExtractor implements ReceiptExtractor {

    private enum Group { TRAVEL, MEALS, EQUIPMENT, SERVICES }

    /** Keywords in a category's name/description that identify each group. */
    private static final Map<Group, List<String>> CATEGORY_KEYWORDS = Map.of(
            Group.TRAVEL, List.of("travel", "di chuyển", "công tác", "khách sạn"),
            Group.MEALS, List.of("meal", "ăn uống", "tiếp khách"),
            Group.EQUIPMENT, List.of("equipment", "thiết bị", "phần mềm", "văn phòng"),
            Group.SERVICES, List.of("outsourc", "dịch vụ", "thuê ngoài"));

    /** Whole-word keywords in an expense summary that identify each group, checked in this order. */
    private static final Map<Group, List<String>> SUMMARY_KEYWORDS = new LinkedHashMap<>();

    static {
        SUMMARY_KEYWORDS.put(Group.EQUIPMENT, List.of("văn phòng phẩm", "thiết bị", "phần mềm", "máy", "license"));
        SUMMARY_KEYWORDS.put(Group.TRAVEL, List.of("di chuyển", "taxi", "grab", "xe", "khách sạn", "vé", "lưu trú"));
        SUMMARY_KEYWORDS.put(Group.MEALS, List.of("ăn", "uống", "tiếp khách", "nhà hàng", "cà phê"));
        SUMMARY_KEYWORDS.put(Group.SERVICES, List.of("dịch vụ", "thuê", "tư vấn"));
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-v1";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public ExtractedReceipt extract(byte[] content, String mimeType, String fileName, List<CategoryCandidate> categories) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String date = LocalDate.now().minusDays(2).toString();

        ExtractedReceipt sample;
        if (containsAny(name, "grab", "taxi", "xe", "be-", "xanh")) {
            sample = receipt("185000", date, "Công ty TNHH Grab", "0312650437", "A-8F2K91", "13704",
                    "di chuyển bằng xe công nghệ",
                    List.of(item("GrabCar 4 chỗ – Quận 1 đến Quận 7", "1", "185000")));
        } else if (containsAny(name, "hotel", "khach-san", "khachsan")) {
            sample = receipt("1800000", date, "Khách sạn Mường Thanh Đà Nẵng", "0401234567", "0001523", "163636",
                    "lưu trú công tác 1 đêm",
                    List.of(item("Phòng Deluxe 1 đêm", "1", "1800000")));
        } else if (containsAny(name, "an-uong", "anuong", "nha-hang", "nhahang", "restaurant", "cafe", "coffee", "food", "bill")) {
            sample = receipt("1250000", date, "Nhà hàng Hương Việt", "0109876543", "0004412", "92593",
                    "ăn trưa tiếp khách",
                    List.of(item("Set menu 4 người", "1", "980000"), item("Đồ uống", "4", "177407")));
        } else {
            sample = receipt("356000", date, "Văn phòng phẩm Hồng Hà", "0100100417", "0012876", "32364",
                    "mua văn phòng phẩm",
                    List.of(item("Giấy A4 Double A", "2", "150000"), item("Bút bi Thiên Long", "20", "173636")));
        }

        Long categoryId = suggestCategory(sample.expenseSummary(), sample.vendorName(), categories);
        return new ExtractedReceipt(sample.totalAmount(), sample.invoiceDate(), sample.vendorName(),
                sample.vendorTaxCode(), sample.invoiceNumber(), sample.vatAmount(), sample.currency(),
                sample.expenseSummary(), sample.lineItems(), categoryId, "{\"mock\":true}", model());
    }

    @Override
    public Long suggestCategory(String expenseSummary, String vendorName, List<CategoryCandidate> categories) {
        String summary = ((expenseSummary == null ? "" : expenseSummary) + " "
                + (vendorName == null ? "" : vendorName)).toLowerCase(Locale.ROOT);
        for (Map.Entry<Group, List<String>> rule : SUMMARY_KEYWORDS.entrySet()) {
            if (!containsAnyWord(summary, rule.getValue())) {
                continue;
            }
            for (CategoryCandidate c : categories) {
                String text = (c.name() + " " + (c.description() == null ? "" : c.description())).toLowerCase(Locale.ROOT);
                if (containsAny(text, CATEGORY_KEYWORDS.get(rule.getKey()).toArray(String[]::new))) {
                    return c.id();
                }
            }
        }
        return null;
    }

    private static ExtractedReceipt receipt(String total, String date, String vendor, String taxCode, String number,
                                            String vat, String summary, List<ExtractedReceipt.LineItem> items) {
        return new ExtractedReceipt(new BigDecimal(total), date, vendor, taxCode, number, new BigDecimal(vat),
                "VND", summary, items, null, null, null);
    }

    private static ExtractedReceipt.LineItem item(String description, String quantity, String amount) {
        return new ExtractedReceipt.LineItem(description, new BigDecimal(quantity), new BigDecimal(amount));
    }

    /** "văn phòng" must not match "ăn": compare on whole words only. */
    private static boolean containsAnyWord(String text, List<String> keywords) {
        String padded = " " + text.replaceAll("[^\\p{L}\\p{N}]+", " ") + " ";
        for (String k : keywords) {
            if (padded.contains(" " + k + " ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
