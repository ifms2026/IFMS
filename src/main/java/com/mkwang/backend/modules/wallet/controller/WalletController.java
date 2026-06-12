package com.mkwang.backend.modules.wallet.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.wallet.dto.response.LedgerEntryResponse;
import com.mkwang.backend.modules.wallet.dto.response.TransactionResponse;
import com.mkwang.backend.modules.wallet.dto.response.WalletResponse;
import com.mkwang.backend.modules.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Current user wallet APIs")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current user wallet balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        Long userId = principal.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(walletService.getMyWallet(userId)));
    }

    @GetMapping("/transactions")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current user wallet transaction history")
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> getMyTransactions(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = principal.getUser().getId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(walletService.getMyTransactions(userId, from, to, pageable)));
    }

    @GetMapping("/transactions/{transactionId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current user wallet transaction detail")
    public ResponseEntity<ApiResponse<TransactionResponse>> getMyTransaction(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @PathVariable Long transactionId) {
        Long userId = principal.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(walletService.getMyTransaction(userId, transactionId)));
    }
}
