package com.mkwang.backend.modules.request.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.request.dto.request.DisburseRequest;
import com.mkwang.backend.modules.request.dto.request.RejectRequestRequest;
import com.mkwang.backend.modules.request.dto.response.AccountantDisbursementDetailResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantDisbursementSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantRejectResponse;
import com.mkwang.backend.modules.request.dto.response.DisburseResponse;
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
@RequestMapping("/accountant/disbursements")
@RequiredArgsConstructor
@Tag(name = "Accountant - Disbursements", description = "Flow 1 execution for ADVANCE/EXPENSE/REIMBURSE")
@SecurityRequirement(name = "bearerAuth")
public class AccountantDisbursementController {

    private final RequestService requestService;

    @GetMapping
    @Operation(
        summary = "List requests awaiting disbursement",
        description = "Returns a paginated list of APPROVED ADVANCE/EXPENSE/REIMBURSE requests across all projects, ready for the Accountant to execute. Filterable by type and keyword search on request code or requester name."
    )
    public ResponseEntity<ApiResponse<PageResponse<AccountantDisbursementSummaryResponse>>> list(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getAccountantDisbursements(type, search, page, size)
        ));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get disbursement detail",
        description = "Returns full detail of an APPROVED request for disbursement review: requester bank info, approved amount, project wallet balance, phase/category budget remaining, and the approval history."
    )
    public ResponseEntity<ApiResponse<AccountantDisbursementDetailResponse>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getAccountantDisbursementDetail(id)
        ));
    }

    @PostMapping("/{id}/disburse")
    @Operation(
        summary = "Execute disbursement",
        description = "For ADVANCE, transfers approved funds and opens an advance balance. For EXPENSE, verifies the employee-funded receipt and records the expense; the separate /pay endpoint transfers the reimbursement. For REIMBURSE, records valid costs against an existing advance without another wallet transfer."
    )
    public ResponseEntity<ApiResponse<DisburseResponse>> disburse(
            @PathVariable Long id,
            @Valid @RequestBody DisburseRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.disburse(id, principal.getUser().getId(), req)
        ));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay a verified employee expense", description = "Transfers the approved reimbursement amount after a valid expense receipt has been recorded.")
    public ResponseEntity<ApiResponse<DisburseResponse>> payExpense(
            @PathVariable Long id,
            @Valid @RequestBody DisburseRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.payExpense(id, principal.getUser().getId(), req)
        ));
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "Reject disbursement",
        description = "Rejects an APPROVED request at the disbursement stage. A reject reason is required. Status moves to REJECTED and the requester is notified. Used when the Accountant identifies a compliance or documentation issue after TL approval."
    )
    public ResponseEntity<ApiResponse<AccountantRejectResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.accountantReject(id, principal.getUser().getId(), req)
        ));
    }
}

