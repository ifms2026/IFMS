package com.mkwang.backend.common.utils;

import com.mkwang.backend.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Singleton Spring Bean — validate & kiểm tra chính sách PIN giao dịch.
 * <p>
 * Tất cả config được inject từ {@code application.yml} → không hardcode.
 * Regex Pattern + weak PIN Set được build 1 lần khi bean khởi tạo.
 *
 * <pre>
 * application.security.pin.length:       5       (PIN_LENGTH env)
 * application.security.pin.max-retry:    5       (PIN_MAX_RETRY env)
 * application.security.pin.lock-minutes: 30      (PIN_LOCK_MINUTES env)
 * </pre>
 */
@Component
public class PinValidator {

    private final int pinLength;
    private final int maxRetry;
    private final int lockMinutes;

    /** Pre-compiled regex — build 1 lần từ pinLength config */
    private final Pattern pinPattern;

    /** O(1) lookup thay vì loop array — immutable Set */
    private static final Set<String> WEAK_PINS = Set.of(
            "00000", "11111", "22222", "33333", "44444",
            "55555", "66666", "77777", "88888", "99999",
            "12345", "54321", "01234", "43210",
            "13579", "97531", "02468", "86420"
    );

    public PinValidator(
            @Value("${application.security.pin.length:5}") int pinLength,
            @Value("${application.security.pin.max-retry:5}") int maxRetry,
            @Value("${application.security.pin.lock-minutes:30}") int lockMinutes) {
        this.pinLength = pinLength;
        this.maxRetry = maxRetry;
        this.lockMinutes = lockMinutes;
        this.pinPattern = Pattern.compile("^\\d{" + pinLength + "}$");
    }

    // ── Getters (cho service layer sử dụng) ────────────────────

    public int getPinLength() { return pinLength; }
    public int getMaxRetry() { return maxRetry; }
    public int getLockMinutes() { return lockMinutes; }

    // ── Validation ─────────────────────────────────────────────

    /** Kiểm tra format: đúng N chữ số (N = pinLength từ config) */
    public boolean isValidFormat(String pin) {
        return pin != null && pinPattern.matcher(pin).matches();
    }

    /** Kiểm tra PIN yếu: lặp số, tuần tự, phổ biến. O(1) lookup via Set.contains() */
    public boolean isWeakPin(String pin) {
        return pin == null || WEAK_PINS.contains(pin);
    }

    /**
     * Full validation: format + strength.
     *
     * @throws IllegalArgumentException nếu PIN không hợp lệ
     */
    public void validate(String pin) {
        if (!isValidFormat(pin)) {
            throw new BadRequestException(
                    "PIN must be exactly " + pinLength + " digits (numeric only).");
        }
        if (isWeakPin(pin)) {
            throw new BadRequestException(
                    "PIN is too weak. Avoid sequential digits (12345) or repeated digits (11111).");
        }
    }

    /** Kiểm tra PIN mới trùng PIN cũ (raw string, trước khi hash) */
    public boolean isSamePin(String currentPin, String newPin) {
        return currentPin != null && currentPin.equals(newPin);
    }

    // ── Retry / Lock ───────────────────────────────────────────

    /** Kiểm tra có nên khóa PIN không (retryCount >= maxRetry từ config) */
    public boolean shouldLock(int retryCount) {
        return retryCount >= maxRetry;
    }
}
