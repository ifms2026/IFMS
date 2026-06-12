package com.mkwang.backend.modules.request.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.request.dto.request.ApproveRequestRequest;
import com.mkwang.backend.modules.request.dto.request.RejectRequestRequest;
import com.mkwang.backend.modules.request.dto.response.TlApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.TlApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.TlApproveResponse;
import com.mkwang.backend.modules.request.dto.response.TlRejectResponse;
import com.mkwang.backend.modules.request.entity.RequestType;
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
@RequestMapping("/team-leader/approvals")
@RequiredArgsConstructor
@Tag(name = "Team Leader - Approvals", description = "Flow 1 approval for ADVANCE/EXPENSE/REIMBURSE")
@SecurityRequirement(name = "bearerAuth")
public class TeamLeaderApprovalController {

    private final RequestService requestService;

    @GetMapping
    @Operation(
        summary = "List requests pending Team Leader approval",
        description = "Returns a paginated list of ADVANCE/EXPENSE/REIMBURSE requests in PENDING_TL status that belong to projects where the authenticated user is LEADER. Filterable by type and projectId."
    )
    public ResponseEntity<ApiResponse<PageResponse<TlApprovalSummaryResponse>>> getApprovals(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getTlApprovals(principal.getUser().getId(), type, projectId, search, page, size)
        ));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get approval detail",
        description = "Returns full detail of a pending request for review: requester info, amount, project/phase/category context, attached evidence files, and approval history."
    )
    public ResponseEntity<ApiResponse<TlApprovalDetailResponse>> getApprovalDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getTlApprovalDetail(id, principal.getUser().getId())
        ));
    }

    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Approve a request (Flow 1)",
        description = "Approves a PENDING_TL request. Status moves to APPROVED and the request enters the Accountant's disbursement queue. An optional comment may be included. Phase and category budget are not deducted here — deduction happens at disbursement."
    )
    public ResponseEntity<ApiResponse<TlApproveResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.approveTlRequest(id, principal.getUser().getId(), req)
        ));
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "Reject a request (Flow 1)",
        description = "Rejects a PENDING_TL request. A reject reason is required. Status moves to REJECTED and the requester is notified. The request cannot be resubmitted — the employee must create a new one."
    )
    public ResponseEntity<ApiResponse<TlRejectResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.rejectTlRequest(id, principal.getUser().getId(), req)
        ));
    }
}

