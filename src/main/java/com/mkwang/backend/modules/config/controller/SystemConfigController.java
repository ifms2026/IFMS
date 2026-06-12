package com.mkwang.backend.modules.config.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.config.dto.response.SystemConfigResponse;
import com.mkwang.backend.modules.config.dto.request.SystemConfigRequest;
import com.mkwang.backend.modules.config.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SystemConfigController — REST API quản lý system configuration.
 *
 * Base URL: /api/v1/system-configs
 * Auth:     SYSTEM_CONFIG_MANAGE (Admin only)
 *
 * Endpoints:
 *   GET    /system-configs          → list tất cả configs
 *   GET    /system-configs/{key}    → lấy 1 config theo key
 *   PUT    /system-configs/{key}    → cập nhật giá trị (cache updated immediately)
 *   POST   /system-configs/{key}    → tạo mới hoặc upsert
 *   DELETE /system-configs/{key}/cache        → evict cache cho 1 key
 *   DELETE /system-configs/cache              → evict toàn bộ cache
 */
@RestController
@RequestMapping("/system-configs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYSTEM_CONFIG_MANAGE')")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * GET /api/v1/system-configs
     * Lấy toàn bộ danh sách system config (luôn từ DB — không cache).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> getAll() {
        List<SystemConfigResponse> configs = systemConfigService.getAll()
                .stream()
                .map(SystemConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    /**
     * GET /api/v1/system-configs/{key}
     * Lấy 1 config theo key (qua Redis cache).
     */
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> getByKey(@PathVariable String key) {
        String value = systemConfigService.getOrDefault(key, null);
        if (value == null) {
            return ResponseEntity.ok(ApiResponse.error("Config key không tồn tại: " + key));
        }
        // Build lightweight DTO from cached value (no DB hit)
        SystemConfigResponse dto = new SystemConfigResponse(key, value, null, null, null);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * PUT /api/v1/system-configs/{key}
     * Cập nhật giá trị config đã tồn tại. Throw 404 nếu key không có.
     * Cache được cập nhật ngay lập tức (@CachePut).
     */
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<String>> update(
            @PathVariable String key,
            @Valid @RequestBody SystemConfigRequest request) {
        systemConfigService.update(key, request.value());
        return ResponseEntity.ok(ApiResponse.success(
                "Config [" + key + "] đã được cập nhật",
                request.value()));
    }

    /**
     * POST /api/v1/system-configs/{key}
     * Tạo mới hoặc upsert config (kèm description).
     */
    @PostMapping("/{key}")
    public ResponseEntity<ApiResponse<String>> set(
            @PathVariable String key,
            @Valid @RequestBody SystemConfigRequest request) {
        systemConfigService.set(key, request.value(), request.description());
        return ResponseEntity.ok(ApiResponse.success(
                "Config [" + key + "] đã được lưu",
                request.value()));
    }

    /**
     * DELETE /api/v1/system-configs/{key}/cache
     * Evict cache cho 1 key cụ thể (không xóa DB).
     * Dùng khi muốn force-reload từ DB trong lần đọc tiếp theo.
     */
    @DeleteMapping("/{key}/cache")
    public ResponseEntity<ApiResponse<Void>> evict(@PathVariable String key) {
        systemConfigService.evict(key);
        return ResponseEntity.ok(ApiResponse.success(
                "Cache evicted cho key: " + key, null));
    }

    /**
     * DELETE /api/v1/system-configs/cache
     * Evict toàn bộ system_configs cache.
     * Dùng sau khi import bulk config hoặc deploy config mới.
     */
    @DeleteMapping("/cache")
    public ResponseEntity<ApiResponse<Void>> evictAll() {
        systemConfigService.evictAll();
        return ResponseEntity.ok(ApiResponse.success(
                "Toàn bộ system_configs cache đã được xóa", null));
    }
}
