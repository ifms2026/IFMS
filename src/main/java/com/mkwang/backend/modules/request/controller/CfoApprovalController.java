package com.mkwang.backend.modules.request.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.request.dto.request.ApproveRequestRequest;
import com.mkwang.backend.modules.request.dto.request.RejectRequestRequest;
import com.mkwang.backend.modules.request.dto.response.CfoApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApproveResponse;
import com.mkwang.backend.modules.request.dto.response.CfoRejectResponse;
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
@RequestMapping("/cfo/approvals")
@RequiredArgsConstructor
@Tag(name = "CFO - Approvals", description = "Flow 3 approval for DEPARTMENT_TOPUP")
@SecurityRequirement(name = "bearerAuth")
public class CfoApprovalController {

    private final RequestService requestService;

    @GetMapping
    @Operation(
        summary = "List DEPARTMENT_TOPUP requests pending CFO approval",
        description = "Returns a paginated list of DEPARTMENT_TOPUP requests in PENDING status. " +
                      "Keyword search applies to request code and description."
    )
    public ResponseEntity<ApiResponse<PageResponse<CfoApprovalSummaryResponse>>> getApprovals(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getCfoApprovals(search, page, size)
        ));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get DEPARTMENT_TOPUP approval detail",
        description = "Full detail of a DEPARTMENT_TOPUP request: requesting Manager, " +
                      "target department budget, and current CompanyFund balance."
    )
    public ResponseEntity<ApiResponse<CfoApprovalDetailResponse>> getApprovalDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getCfoApprovalDetail(id)
        ));
    }

    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Approve DEPARTMENT_TOPUP (Flow 3)",
        description = "Approves a DEPARTMENT_TOPUP request. Funds are immediately transferred " +
                      "from CompanyFund to the Department wallet (DEPT_QUOTA_ALLOCATION). " +
                      "Status transitions APPROVED_BY_CFO → PAID in the same operation."
    )
    public ResponseEntity<ApiResponse<CfoApproveResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.approveCfoRequest(id, principal.getUser().getId(), req)
        ));
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "Reject DEPARTMENT_TOPUP (Flow 3)",
        description = "Rejects a DEPARTMENT_TOPUP request. A reject reason is required. " +
                      "No funds are moved. Status moves to REJECTED."
    )
    public ResponseEntity<ApiResponse<CfoRejectResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.rejectCfoRequest(id, principal.getUser().getId(), req)
        ));
    }
}
