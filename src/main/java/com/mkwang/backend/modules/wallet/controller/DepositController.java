package com.mkwang.backend.modules.wallet.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.wallet.dto.request.CreateDepositRequest;
import com.mkwang.backend.modules.wallet.dto.response.DepositLogResponse;
import com.mkwang.backend.modules.wallet.entity.DepositStatus;
import com.mkwang.backend.modules.wallet.service.depositing.DepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * DepositController — VNPay deposit initiation and history.
 *
 * Base URL: /wallet/deposit
 *
 *   POST /wallet/deposit        → create deposit intent, returns VNPay payment URL
 *   GET  /wallet/deposit/my     → current user's deposit history
 *
 * IPN callback is handled by PaymentController (/payments/vnpay/ipn) — public endpoint.
 */
@RestController
@RequestMapping("/wallet/deposit")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;

    @PostMapping
    @Operation(summary = "Tạo yêu cầu nạp tiền qua VNPay. Trả về payment URL để redirect user.")
    public ResponseEntity<ApiResponse<DepositLogResponse>> createDeposit(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @Valid @RequestBody CreateDepositRequest request,
            HttpServletRequest httpRequest) {
        Long userId = userDetails.getUser().getId();
        String ipAddress = httpRequest.getRemoteAddr();
        DepositLogResponse response = depositService.createDeposit(userId, request, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo yêu cầu nạp tiền thành công", response));
    }

    @GetMapping("/my")
    @Operation(summary = "Lịch sử nạp tiền của user hiện tại. Hỗ trợ filter theo status, from, to.")
    public ResponseEntity<ApiResponse<PageResponse<DepositLogResponse>>> getMyDeposits(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) DepositStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = userDetails.getUser().getId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                depositService.getMyDeposits(userId, status, from, to, pageable)));
    }
}
