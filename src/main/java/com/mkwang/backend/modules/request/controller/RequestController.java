package com.mkwang.backend.modules.request.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.request.dto.request.CreateRequestRequest;
import com.mkwang.backend.modules.request.dto.request.UpdateRequestRequest;
import com.mkwang.backend.modules.request.dto.response.AdvanceBalanceItem;
import com.mkwang.backend.modules.request.dto.response.RequestDetailResponse;
import com.mkwang.backend.modules.request.dto.response.RequestSummaryResponse;
import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;
import com.mkwang.backend.modules.request.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
@Tag(name = "Request", description = "Employee request management")
@SecurityRequirement(name = "bearerAuth")
public class RequestController {

    private final RequestService requestService;

    @GetMapping
    @Operation(
        summary = "List my requests",
        description = "Returns a paginated list of the authenticated user's requests. Filterable by type (ADVANCE/EXPENSE/REIMBURSE/PROJECT_TOPUP/DEPARTMENT_TOPUP), status, and keyword search on request code or description."
    )
    public ResponseEntity<ApiResponse<PageResponse<RequestSummaryResponse>>> getMyRequests(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getMyRequests(principal.getUser().getId(), type, status, search, page, limit)
        ));
    }

    @GetMapping("/summary")
    @Operation(
        summary = "Get my request summary",
        description = "Returns aggregated counts of the authenticated user's requests grouped by status (DRAFT, PENDING_TL, APPROVED, REJECTED, PAID, etc.). Role-aware: Team Leader also gets pending approval counts."
    )
    public ResponseEntity<ApiResponse<Object>> getMyRequestSummary(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        String roleName = principal.getUser().getRole() != null ? principal.getUser().getRole().getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getMyRequestSummary(principal.getUser().getId(), roleName)
        ));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get request detail",
        description = "Returns full detail of a single request including approval history and attached files. Only the requester or an approver in the chain may access it."
    )
    public ResponseEntity<ApiResponse<RequestDetailResponse>> getRequestDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getRequestDetail(id, principal.getUser().getId())
        ));
    }

    @PostMapping
    @Operation(
        summary = "Create a new request",
        description = "Creates a new expense request (ADVANCE, EXPENSE, or REIMBURSE). Requires an ACTIVE project + ACTIVE phase with sufficient PhaseCategoryBudget. REIMBURSE must reference an open advance_balance_id."
    )
    public ResponseEntity<ApiResponse<RequestDetailResponse>> createRequest(
            @Valid @RequestBody CreateRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                requestService.createRequest(req, principal.getUser().getId())
        ));
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a draft request",
        description = "Updates amount, description, or category of a request that is still in DRAFT status. Only the original requester may update. Requests that have been submitted for approval cannot be modified."
    )
    public ResponseEntity<ApiResponse<RequestDetailResponse>> updateRequest(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.updateRequest(id, req, principal.getUser().getId())
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Cancel a pending request",
        description = "Cancels a request that is in DRAFT or PENDING_TL status. Only the original requester may cancel. Requests already approved or paid cannot be cancelled."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        requestService.cancelRequest(id, principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Request cancelled successfully")));
    }

    @GetMapping("/my-advance-balances")
    @Operation(
        summary = "List my outstanding advance balances",
        description = "Returns all OUTSTANDING or PARTIALLY_SETTLED advance balances for the current user. Used to populate the advance-balance selector when creating a REIMBURSE request."
    )
    public ResponseEntity<ApiResponse<List<AdvanceBalanceItem>>> getMyAdvanceBalances(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getMyAdvanceBalances(principal.getUser().getId())));
    }
}

