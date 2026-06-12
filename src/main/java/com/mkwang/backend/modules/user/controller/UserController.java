package com.mkwang.backend.modules.user.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.sse.SseService;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.user.dto.request.OnboardUserRequest;
import com.mkwang.backend.modules.user.dto.response.OnboardUserResponse;
import com.mkwang.backend.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User management APIs")
public class UserController {

    private final UserService userService;
    private final SseService sseService;

    // ── GET /users/stream ────────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Open SSE stream — single connection for all real-time events (wallet.updated, transaction.created, notification)")
    public SseEmitter stream(@AuthenticationPrincipal UserDetailsAdapter principal) {
        return sseService.connect(principal.getUser().getId());
    }

    // ── GET /users/project/{projectId}/stream ────────────────────

    @GetMapping(value = "/project/{projectId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('REQUEST_APPROVE_TEAM_LEADER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Open SSE stream for a project wallet — wallet.updated and transaction.created events (Team Leader only)")
    public SseEmitter projectWalletStream(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return sseService.connectToWallet(principal.getUser().getId(), "PROJECT:" + projectId);
    }

    // ── GET /users/department/{departmentId}/stream ───────────────

    @GetMapping(value = "/department/{departmentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('REQUEST_APPROVE_PROJECT_TOPUP')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Open SSE stream for a department wallet — wallet.updated and transaction.created events (Manager only)")
    public SseEmitter departmentWalletStream(
            @PathVariable Long departmentId,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return sseService.connectToWallet(principal.getUser().getId(), "DEPARTMENT:" + departmentId);
    }

    // ── GET /users/company-fund/stream ───────────────────────────

    @GetMapping(value = "/company-fund/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('WALLET_VIEW_ALL')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Open SSE stream for the company fund wallet — wallet.updated and transaction.created events (CFO/Accountant only)")
    public SseEmitter companyFundStream(@AuthenticationPrincipal UserDetailsAdapter principal) {
        return sseService.connectToWallet(principal.getUser().getId(), "COMPANY_FUND:1");
    }


}
