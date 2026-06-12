package com.mkwang.backend.modules.wallet.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.wallet.dto.request.CreateWithdrawRequest;
import com.mkwang.backend.modules.wallet.dto.request.ProcessWithdrawRequest;
import com.mkwang.backend.modules.wallet.dto.response.WithdrawRequestResponse;
import com.mkwang.backend.modules.wallet.entity.WithdrawStatus;
import com.mkwang.backend.modules.wallet.service.withdrawing.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * WithdrawController — REST endpoints for withdrawal management.
 *
 * Base URL: /withdrawals
 *
 * User endpoints (WALLET_WITHDRAW):
 *   POST   /withdrawals        → create (auto-executes if within role limit)
 *   DELETE /withdrawals/{id}   → cancel own PENDING request
 *   GET    /withdrawals/my     → my history
 *
 * Accountant endpoints (TRANSACTION_APPROVE_WITHDRAW):
 *   GET /withdrawals            → all requests (filter by ?status=)
 *   PUT /withdrawals/{id}/execute → call MockBank on PENDING request
 *   PUT /withdrawals/{id}/reject  → reject + unlock funds
 */
@RestController
@RequestMapping("/wallet/withdraw")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WithdrawController {

    private final WithdrawService withdrawService;

    // ══════════════════════════════════════════════════════════════════
    //  USER ENDPOINTS
    // ══════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/withdrawals
     * User tạo yêu cầu rút tiền. Bank info đọc từ UserProfile tự động.
     * Auth: WALLET_WITHDRAW
     */
    @PostMapping
    @Operation(summary = "Tạo yêu cầu rút tiền mới. Nếu số tiền rút <= hạn mức role, yêu cầu sẽ được tự động thực thi.")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> createRequest(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @Valid @RequestBody CreateWithdrawRequest request) {
        Long userId = userDetails.getUser().getId();
        WithdrawRequestResponse dto = withdrawService.createRequest(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Yêu cầu rút tiền đã được tạo", dto));
    }

    /**
     * DELETE /api/v1/withdrawals/{id}
     * User tự hủy yêu cầu (chỉ khi PENDING).
     * Auth: WALLET_WITHDRAW (chỉ owner)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> cancelRequest(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @PathVariable Long id) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu rút tiền đã được hủy",
                withdrawService.cancelRequest(userId, id)));
    }

    /**
     * GET /api/v1/withdrawals/my?page=0&size=10
     * Lịch sử rút tiền của chính user đang đăng nhập.
     * Auth: WALLET_WITHDRAW
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawRequestResponse>>> getMyRequests(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = userDetails.getUser().getId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(withdrawService.getMyRequests(userId, pageable)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  ACCOUNTANT ENDPOINTS
    // ══════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/withdrawals?status=PENDING&page=0&size=20
     * Danh sách tất cả yêu cầu rút tiền (Accountant quản lý).
     * Auth: TRANSACTION_APPROVE_WITHDRAW
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WithdrawRequestResponse>>> getAllRequests(
            @RequestParam(required = false) WithdrawStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(withdrawService.getAllRequests(status, pageable)));
    }


    /**
     * PUT /api/v1/withdrawals/{id}/execute
     * Accountant thực thi chuyển khoản qua MockBank (yêu cầu vượt hạn mức tự động duyệt).
     * Auth: TRANSACTION_APPROVE_WITHDRAW
     */
    @PutMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> executeWithdraw(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) ProcessWithdrawRequest request) {
        Long accountantId = userDetails.getUser().getId();
        String note = request != null ? request.note() : null;
        return ResponseEntity.ok(ApiResponse.success(withdrawService.executeWithdraw(accountantId, id, note)));
    }

    /**
     * PUT /api/v1/withdrawals/{id}/reject
     * Accountant từ chối yêu cầu, funds được unlock.
     * Auth: TRANSACTION_APPROVE_WITHDRAW
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> rejectRequest(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @PathVariable Long id,
            @RequestBody ProcessWithdrawRequest request) {
        Long accountantId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu rút tiền đã bị từ chối",
                withdrawService.rejectRequest(accountantId, id, request.note())));
    }
}
