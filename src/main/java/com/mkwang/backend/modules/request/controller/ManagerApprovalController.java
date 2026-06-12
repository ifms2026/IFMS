package com.mkwang.backend.modules.request.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.request.dto.request.ApproveRequestRequest;
import com.mkwang.backend.modules.request.dto.request.RejectRequestRequest;
import com.mkwang.backend.modules.request.dto.response.ManagerApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApproveResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerRejectResponse;
import com.mkwang.backend.modules.request.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manager/approvals")
@RequiredArgsConstructor
@Tag(name = "Manager - Approvals", description = "Flow 2 approval for PROJECT_TOPUP")
@SecurityRequirement(name = "bearerAuth")
public class ManagerApprovalController {

    private final RequestService requestService;

    @GetMapping
    @Operation(
        summary = "List PROJECT_TOPUP requests pending Manager approval",
        description = "Returns a paginated list of PROJECT_TOPUP requests in PENDING_MANAGER status that belong to projects under the authenticated manager's department. Keyword search applies to request code and description."
    )
    public ResponseEntity<ApiResponse<PageResponse<ManagerApprovalSummaryResponse>>> getApprovals(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getManagerApprovals(principal.getUser().getId(), search, page, size)
        ));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get PROJECT_TOPUP approval detail",
        description = "Returns full detail of a PROJECT_TOPUP request: requesting Team Leader, target project, requested amount, current project budget, department available balance, and the approval history chain."
    )
    public ResponseEntity<ApiResponse<ManagerApprovalDetailResponse>> getApprovalDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getManagerApprovalDetail(id, principal.getUser().getId())
        ));
    }

    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Approve PROJECT_TOPUP (Flow 2)",
        description = "Approves a PROJECT_TOPUP request. Funds are immediately transferred from the Department wallet to the Project wallet (Dept Fund → Project Fund). Department available_balance and project available_budget are updated atomically."
    )
    public ResponseEntity<ApiResponse<ManagerApproveResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.approveManagerRequest(id, principal.getUser().getId(), req)
        ));
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "Reject PROJECT_TOPUP (Flow 2)",
        description = "Rejects a PROJECT_TOPUP request. A reject reason is required. No funds are moved. Status moves to REJECTED and the Team Leader is notified."
    )
    public ResponseEntity<ApiResponse<ManagerRejectResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.rejectManagerRequest(id, principal.getUser().getId(), req)
        ));
    }
}

