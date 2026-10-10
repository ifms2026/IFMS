package com.mkwang.backend.modules.ai.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.ai.dto.request.CategorySuggestionRequest;
import com.mkwang.backend.modules.ai.dto.response.AiStatusResponse;
import com.mkwang.backend.modules.ai.dto.response.CategorySuggestionResponse;
import com.mkwang.backend.modules.ai.dto.response.ReceiptExtractionResponse;
import com.mkwang.backend.modules.ai.service.ReceiptExtractionService;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiReceiptController {

    private final ReceiptExtractionService receiptExtractionService;

    @GetMapping("/status")
    @Operation(summary = "AI receipt scan status", description = "Whether the feature is on and how many scans the user has left today.")
    public ResponseEntity<ApiResponse<AiStatusResponse>> getStatus(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(receiptExtractionService.getStatus(principal.getUser())));
    }

    @PostMapping(value = "/receipts/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Read a receipt with AI",
            description = "Reads amount, date, seller, invoice number and line items from a JPG/PNG/WEBP/PDF receipt. "
                    + "With phaseId, also picks one of that phase's existing expense categories (or none).")
    public ResponseEntity<ApiResponse<ReceiptExtractionResponse>> extract(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long phaseId) {
        return ResponseEntity.ok(ApiResponse.success(
                receiptExtractionService.extract(file, phaseId, principal.getUser())));
    }

    @PostMapping("/receipts/{extractionId}/category-suggestion")
    @Operation(summary = "Pick a category for an earlier scan",
            description = "Used when the user selects the phase after uploading the receipt. Does not re-send the image.")
    public ResponseEntity<ApiResponse<CategorySuggestionResponse>> suggestCategory(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long extractionId,
            @Valid @RequestBody CategorySuggestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                receiptExtractionService.suggestCategory(extractionId, request.getPhaseId(), principal.getUser())));
    }
}
