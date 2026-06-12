package com.mkwang.backend.common.utils.businesscodegenerator;

import com.mkwang.backend.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Shared helper for all {@link BusinessCodeStrategy} implementations.
 * <p>
 * Singleton Spring Bean — {@code SecureRandom} và {@code HexFormat} được khởi tạo 1 lần,
 * regex Pattern được pre-compile, tái sử dụng toàn bộ vòng đời application.
 */
@Component
public class CodeFormatUtils {

    private final SecureRandom secureRandom;

    /** Pre-compiled regex — tránh compile lại mỗi lần gọi sanitizeSlug() */
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Z0-9]");

    /** Cached HexFormat — tránh tạo instance mới mỗi lần gọi randomHex() */
    private static final HexFormat HEX_UPPER = HexFormat.of().withUpperCase();

    public CodeFormatUtils() {
        this.secureRandom = new SecureRandom();
    }

    /** Test constructor — inject mock SecureRandom */
    CodeFormatUtils(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * Sanitize a slug: uppercase → strip non-alphanumeric → truncate.
     */
    public String sanitizeSlug(String input, int maxLen) {
        if (input == null || input.isBlank()) {
            throw new BadRequestException("Slug/code must not be null or blank");
        }
        String slug = NON_ALPHANUM.matcher(input.trim().toUpperCase()).replaceAll("");
        if (slug.isEmpty()) {
            throw new BadRequestException("Slug/code contains no valid characters: " + input);
        }
        return slug.length() > maxLen ? slug.substring(0, maxLen) : slug;
    }

    /**
     * Generate random uppercase hex string.
     * <p>
     * 1 lần nextBytes() thay vì N lần nextInt() — giảm synchronized locks từ O(n) → O(1).
     */
    public String randomHex(int length) {
        int byteLength = (length + 1) / 2;
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        String hex = HEX_UPPER.formatHex(bytes);
        return hex.substring(0, length);
    }

    /**
     * Zero-pad số về bên trái đến đủ width ký tự.
     * <p>
     * Nhanh hơn {@code String.format("%0Xd", val)} vì không parse format string.
     *
     * @param val   giá trị cần pad (e.g. 3)
     * @param width độ rộng tối thiểu (e.g. 2 → "03", 3 → "003")
     * @return zero-padded string
     */
    public static String padLeft(long val, int width) {
        String s = Long.toString(val);
        if (s.length() >= width) return s;
        return "0".repeat(width - s.length()) + s;
    }
}

