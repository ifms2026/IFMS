package com.mkwang.backend.modules.profile.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.profile.dto.response.BankOptionResponse;
import com.mkwang.backend.modules.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/banks")
@RequiredArgsConstructor
@Tag(name = "Bank", description = "Supported bank list APIs")
public class BankController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get supported bank list")
    public ResponseEntity<ApiResponse<List<BankOptionResponse>>> getSupportedBanks() {
        return ResponseEntity.ok(ApiResponse.success(profileService.getSupportedBanks()));
    }
}

