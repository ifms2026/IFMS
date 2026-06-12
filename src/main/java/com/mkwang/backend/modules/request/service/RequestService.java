package com.mkwang.backend.modules.request.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.request.dto.request.ApproveRequestRequest;
import com.mkwang.backend.modules.request.dto.request.CreateRequestRequest;
import com.mkwang.backend.modules.request.dto.request.DisburseRequest;
import com.mkwang.backend.modules.request.dto.request.RejectRequestRequest;
import com.mkwang.backend.modules.request.dto.request.UpdateRequestRequest;
import com.mkwang.backend.modules.request.dto.response.AccountantDisbursementDetailResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantDisbursementSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantRejectResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApproveResponse;
import com.mkwang.backend.modules.request.dto.response.CfoRejectResponse;
import com.mkwang.backend.modules.request.dto.response.DisburseResponse;
import com.mkwang.backend.modules.request.dto.response.RequestDetailResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApproveResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerRejectResponse;
import com.mkwang.backend.modules.request.dto.response.RequestSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.TlApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.TlApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.TlApproveResponse;
import com.mkwang.backend.modules.request.dto.response.TlRejectResponse;
import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;

import com.mkwang.backend.modules.request.dto.response.AdvanceBalanceItem;
import com.mkwang.backend.modules.request.dto.response.CfoDeptTopupItemResponse;

import java.math.BigDecimal;
import java.util.List;

public interface RequestService {

    PageResponse<RequestSummaryResponse> getMyRequests(
            Long userId, RequestType type, RequestStatus status, String search, int page, int limit);

    Object getMyRequestSummary(Long userId, String roleName);

    RequestDetailResponse getRequestDetail(Long id, Long userId);

    RequestDetailResponse createRequest(CreateRequestRequest req, Long userId);

    RequestDetailResponse updateRequest(Long id, UpdateRequestRequest req, Long userId);

    void cancelRequest(Long id, Long userId);

    List<AdvanceBalanceItem> getMyAdvanceBalances(Long userId);

    PageResponse<TlApprovalSummaryResponse> getTlApprovals(
            Long leaderId, RequestType type, Long projectId, String search, int page, int size);

    TlApprovalDetailResponse getTlApprovalDetail(Long id, Long leaderId);

    TlApproveResponse approveTlRequest(Long id, Long leaderId, ApproveRequestRequest req);

    TlRejectResponse rejectTlRequest(Long id, Long leaderId, RejectRequestRequest req);

    PageResponse<ManagerApprovalSummaryResponse> getManagerApprovals(Long managerId, String search, int page, int size);

    ManagerApprovalDetailResponse getManagerApprovalDetail(Long id, Long managerId);

    ManagerApproveResponse approveManagerRequest(Long id, Long managerId, ApproveRequestRequest req);

    ManagerRejectResponse rejectManagerRequest(Long id, Long managerId, RejectRequestRequest req);

    PageResponse<CfoApprovalSummaryResponse> getCfoApprovals(String search, int page, int size);

    CfoApprovalDetailResponse getCfoApprovalDetail(Long id);

    CfoApproveResponse approveCfoRequest(Long id, Long cfoId, ApproveRequestRequest req);

    CfoRejectResponse rejectCfoRequest(Long id, Long cfoId, RejectRequestRequest req);

    PageResponse<AccountantDisbursementSummaryResponse> getAccountantDisbursements(
            RequestType type, String search, int page, int size);

    AccountantDisbursementDetailResponse getAccountantDisbursementDetail(Long id);

    DisburseResponse disburse(Long id, Long accountantId, DisburseRequest req);

    AccountantRejectResponse accountantReject(Long id, Long accountantId, RejectRequestRequest req);

    // ── Dashboard aggregates ──────────────────────────────────────────

    long countPendingDisbursements();

    long countDeptPendingProjectTopup(Long deptId);

    BigDecimal sumDeptOutstandingAdvanceDebt(Long deptId);

    long countDeptEmployeesWithDebt(Long deptId);

    long countPendingDeptTopup();

    BigDecimal sumMonthlyApprovedDeptTopup(int year, int month);

    long countMonthlyRejectedDeptTopup(int year, int month);

    List<CfoDeptTopupItemResponse> getRecentDeptTopups(int limit);

    /**
     * Total outstanding advance debt for a user across all unsettled AdvanceBalance records.
     * Used by payroll auto-netting to compute per-payslip advance deductions.
     */
    BigDecimal getTotalOutstandingDebt(Long userId);

    /**
     * Apply a payroll advance deduction against a user's unsettled AdvanceBalance records.
     * Reduces remainingAmount FIFO across advance records until {@code amount} is consumed.
     * No wallet movement — the salary simply wasn't credited.
     * Must be called within a transaction (propagates REQUIRED).
     */
    void applyPayrollDeduction(Long userId, BigDecimal amount);
}

