package com.mkwang.backend.modules.config.service;

import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.config.entity.SystemConfig;
import com.mkwang.backend.modules.config.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SystemConfigServiceImpl — caching layer on top of SystemConfigRepository.
 *
 * Cache: "system_configs" (Redis) — TTL cấu hình tại
 * spring.cache.redis.time-to-live
 * Key strategy: config key string, e.g. "WITHDRAW_LIMIT_EMPLOYEE"
 *
 * Flow:
 * get() → @Cacheable → Redis HIT → return; MISS → DB → store in Redis → return
 * set() → DB upsert → @CachePut → update Redis ngay (không cần đợi expire)
 * update() → DB update → @CachePut → update Redis ngay
 * evict() → @CacheEvict → xóa key khỏi Redis; lần sau sẽ load lại từ DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final String CACHE_NAME = "system_configs";

    private final SystemConfigRepository systemConfigRepository;
    private final CacheManager cacheManager;

    // ══════════════════════════════════════════════════════════════════
    // LIST ALL (no cache — always fresh for admin)
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfig> getAll() {
        return systemConfigRepository.findAll();
    }

    // ══════════════════════════════════════════════════════════════════
    // READ (Redis first, then DB)
    // ══════════════════════════════════════════════════════════════════

    /**
     * @Cacheable: key = configKey.
     *             Returns Optional<String> so null is never cached
     *             (disableCachingNullValues = true).
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#key", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        log.debug("[SystemConfig] Cache MISS — loading key='{}' from DB", key);
        return systemConfigRepository.findById(key)
                .map(SystemConfig::getValue);
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Override
    public int getAsInt(String key, int defaultValue) {
        return get(key)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        log.warn("[SystemConfig] key='{}' value='{}' is not a valid int, using default={}", key, v,
                                defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    @Override
    public long getAsLong(String key, long defaultValue) {
        return get(key)
                .map(v -> {
                    try {
                        return Long.parseLong(v);
                    } catch (NumberFormatException e) {
                        log.warn("[SystemConfig] key='{}' value='{}' is not a valid long, using default={}", key, v,
                                defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    @Override
    public boolean getAsBoolean(String key, boolean defaultValue) {
        return get(key)
                .map(v -> {
                    if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
                        return Boolean.parseBoolean(v);
                    }
                    log.warn("[SystemConfig] key='{}' value='{}' is not a valid boolean, using default={}", key, v,
                            defaultValue);
                    return defaultValue;
                })
                .orElse(defaultValue);
    }

    @Override
    public BigDecimal getAsBigDecimal(String key, BigDecimal defaultValue) {
        return get(key)
                .map(v -> {
                    try {
                        return new BigDecimal(v);
                    } catch (NumberFormatException e) {
                        log.warn("[SystemConfig] key='{}' value='{}' is not a valid BigDecimal, using default={}", key,
                                v, defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    // ══════════════════════════════════════════════════════════════════
    // WRITE (DB + update Redis immediately)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Upsert: tạo mới nếu chưa có, cập nhật nếu đã tồn tại.
     * 
     * @CachePut: luôn ghi vào Redis sau khi lưu DB — bỏ qua cache cũ.
     */
    @Override
    @CachePut(cacheNames = CACHE_NAME, key = "#key")
    @Transactional
    public String set(String key, String value, String description) {
        SystemConfig config = systemConfigRepository.findById(key)
                .orElse(SystemConfig.builder()
                        .key(key)
                        .description(description)
                        .build());
        config.setValue(value);
        systemConfigRepository.save(config);
        log.info("[SystemConfig] set key='{}' value='{}'", key, value);
        return value; // returned value is stored in cache
    }

    /**
     * Cập nhật giá trị config đã có.
     * Throw ResourceNotFoundException nếu key không tồn tại.
     */
    @Override
    @CachePut(cacheNames = CACHE_NAME, key = "#key")
    @Transactional
    public String update(String key, String value) {
        SystemConfig config = systemConfigRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("SystemConfig", "key", key));
        config.setValue(value);
        systemConfigRepository.save(config);
        log.info("[SystemConfig] updated key='{}' value='{}'", key, value);
        return value; // returned value is stored in cache
    }

    // ══════════════════════════════════════════════════════════════════
    // CACHE EVICTION
    // ══════════════════════════════════════════════════════════════════

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, key = "#key")
    public void evict(String key) {
        log.info("[SystemConfig] Cache evicted for key='{}'", key);
    }

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public void evictAll() {
        log.info("[SystemConfig] Cache cleared — all system_configs entries evicted");
    }

    @Override
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG_MANAGE')")
    @Transactional(readOnly = true)
    public List<SystemConfig> getAllForAdmin() {
        return systemConfigRepository.findAll();
    }

    @Override
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG_MANAGE')")
    @Transactional
    public List<SystemConfig> batchUpdate(Map<String, String> configs) {
        configs.forEach((key, value) -> {
            SystemConfig config = systemConfigRepository.findById(key)
                    .orElseThrow(() -> new ResourceNotFoundException("SystemConfig", "key", key));
            config.setValue(value);
            systemConfigRepository.save(config);
            log.info("[SystemConfig] batch updated key='{}' value='{}'", key, value);
        });
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) cache.clear();
        return systemConfigRepository.findAll();
    }
}
