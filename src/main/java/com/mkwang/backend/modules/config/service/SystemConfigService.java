package com.mkwang.backend.modules.config.service;

import com.mkwang.backend.modules.config.entity.SystemConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * SystemConfigService — read/write hệ thống cấu hình với Redis cache.
 *
 * Cache name: {@code system_configs}
 * Cache key:  config key string (e.g. "WITHDRAW_LIMIT_EMPLOYEE")
 * TTL:        mặc định 1 giờ — cấu hình qua {@code spring.cache.redis.time-to-live}
 *
 * Cách hoạt động:
 * <ul>
 *   <li><b>get()</b>  — @Cacheable: đọc từ Redis trước, miss thì query DB rồi cache lại</li>
 *   <li><b>set()</b>  — @CachePut:  lưu DB và cập nhật cache ngay lập tức</li>
 *   <li><b>evict()</b> — @CacheEvict: xóa cache theo key (dùng khi xóa config)</li>
 * </ul>
 */
public interface SystemConfigService {

    /**
     * Lấy toàn bộ danh sách config (không cache — dùng cho admin list).
     */
    List<SystemConfig> getAll();

    /**
     * Lấy giá trị config dạng String.
     * @param key Config key (e.g. "WITHDRAW_LIMIT_EMPLOYEE")
     * @return Giá trị config, hoặc empty nếu không tồn tại
     */
    Optional<String> get(String key);

    /**
     * Lấy giá trị config, fallback về defaultValue nếu không tồn tại.
     */
    String getOrDefault(String key, String defaultValue);

    /**
     * Lấy giá trị config dạng Integer.
     * @throws NumberFormatException nếu value không phải số
     */
    int getAsInt(String key, int defaultValue);

    /**
     * Lấy giá trị config dạng Long.
     */
    long getAsLong(String key, long defaultValue);

    /**
     * Lấy giá trị config dạng Boolean.
     */
    boolean getAsBoolean(String key, boolean defaultValue);

    /**
     * Lấy giá trị config dạng BigDecimal.
     */
    BigDecimal getAsBigDecimal(String key, BigDecimal defaultValue);

    /**
     * Tạo mới hoặc cập nhật giá trị config. Ghi DB và cập nhật cache.
     * Returns giá trị mới (dùng bởi @CachePut để lưu vào Redis).
     *
     * @param key         Config key
     * @param value       Giá trị mới
     * @param description Mô tả (chỉ dùng khi tạo mới; update không đổi description)
     */
    String set(String key, String value, String description);

    /**
     * Cập nhật giá trị config đã có (không thay đổi description).
     * Returns giá trị mới (dùng bởi @CachePut để lưu vào Redis).
     * Throw {@code ResourceNotFoundException} nếu key không tồn tại.
     */
    String update(String key, String value);

    /**
     * Xóa cache cho key cụ thể (không xóa DB).
     * Dùng khi cần force-reload từ DB trong lần đọc tiếp theo.
     */
    void evict(String key);

    /**
     * Xóa toàn bộ cache system_configs.
     * Dùng sau khi import bulk config hoặc khi deploy config mới.
     */
    void evictAll();

    /**
     * Lấy toàn bộ danh sách config cho admin (yêu cầu SYSTEM_CONFIG_MANAGE).
     */
    List<SystemConfig> getAllForAdmin();

    /**
     * Cập nhật hàng loạt config theo map key → value.
     * Chỉ cập nhật các key đã tồn tại; throw ResourceNotFoundException nếu key không có.
     * Evict toàn bộ cache sau khi hoàn tất.
     * Returns danh sách đầy đủ configs sau update.
     */
    List<SystemConfig> batchUpdate(java.util.Map<String, String> configs);
}
