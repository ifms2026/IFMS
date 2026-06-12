package com.mkwang.backend.modules.dashboard.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.dashboard.dto.response.AccountantDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CashFlowAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CfoDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.EmployeeSpendingAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.ManagerDashboardResponse;
import com.mkwang.backend.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Role-specific dashboard snapshots")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/manager")
    @Operation(
        summary = "Manager dashboard snapshot",
        description = "Returns aggregated department budget, project status summary, pending approvals count, and team debt summary for the authenticated manager."
    )
    public ResponseEntity<ApiResponse<ManagerDashboardResponse>> getManagerDashboard(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getManagerDashboard(principal.getUser().getId())
        ));
    }

    @GetMapping("/accountant")
    @Operation(
        summary = "Accountant dashboard snapshot",
        description = "Returns company fund balance, pending disbursements count, monthly inflow/outflow, and latest payroll period status."
    )
    public ResponseEntity<ApiResponse<AccountantDashboardResponse>> getAccountantDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getAccountantDashboard()
        ));
    }

    @GetMapping("/cfo")
    @Operation(
        summary = "CFO dashboard snapshot",
        description = "Returns company fund balance, pending department topup approvals count, and monthly approval statistics."
    )
    public ResponseEntity<ApiResponse<CfoDashboardResponse>> getCfoDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getCfoDashboard()
        ));
    }

    @GetMapping("/admin")
    @Operation(
        summary = "Admin dashboard snapshot",
        description = "Returns total users, departments, aggregate wallet balance, and recent audit events."
    )
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getAdminDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getAdminDashboard()
        ));
    }

    @GetMapping("/analytics/cashflow")
    @Operation(
        summary = "Company fund cash flow analytics by period",
        description = "Returns monthly inflow/outflow for the company fund. " +
                      "period: ytd | last6m | fy{year}. unit: raw | million."
    )
    public ResponseEntity<ApiResponse<CashFlowAnalyticsResponse>> getCashFlowAnalytics(
            @RequestParam(defaultValue = "last6m") String period,
            @RequestParam(defaultValue = "raw") String unit) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getCashFlowAnalytics(period, unit)
        ));
    }

    @GetMapping("/admin/analytics")
    @Operation(
        summary = "Admin-only analytics: department spending (MTD) and top advance debtors"
    )
    public ResponseEntity<ApiResponse<AdminAnalyticsResponse>> getAdminAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getAdminAnalytics()
        ));
    }

    @GetMapping("/analytics/employee")
    @Operation(
        summary = "Employee spending analytics — last 6 months",
        description = "Returns monthly breakdown of EXPENSE/REIMBURSE (chiTieu) and ADVANCE (tamUng) amounts paid for the authenticated employee."
    )
    public ResponseEntity<ApiResponse<EmployeeSpendingAnalyticsResponse>> getEmployeeSpendingAnalytics(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getEmployeeSpendingAnalytics(principal.getUser().getId())
        ));
    }
}
