package com.mkwang.backend.modules.config.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.config.dto.request.UpdateSettingsRequest;
import com.mkwang.backend.modules.config.dto.response.SettingsListResponse;
import com.mkwang.backend.modules.config.dto.response.SystemConfigResponse;
import com.mkwang.backend.modules.config.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsListResponse>> getSettings() {
        List<SystemConfigResponse> items = systemConfigService.getAllForAdmin().stream()
                .map(SystemConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(new SettingsListResponse(items)));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsListResponse>> updateSettings(
            @Valid @RequestBody UpdateSettingsRequest request) {
        Map<String, String> configMap = request.configs().stream()
                .collect(Collectors.toMap(
                        UpdateSettingsRequest.ConfigEntry::key,
                        UpdateSettingsRequest.ConfigEntry::value));
        List<SystemConfigResponse> items = systemConfigService.batchUpdate(configMap).stream()
                .map(SystemConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(new SettingsListResponse(items)));
    }
}
