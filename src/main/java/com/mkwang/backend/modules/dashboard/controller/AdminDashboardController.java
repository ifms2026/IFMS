package com.mkwang.backend.modules.dashboard.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminDashboardResponse;
import com.mkwang.backend.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin management and dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    @Operation(
        summary = "Admin dashboard snapshot",
        description = "Returns total active users, total departments, sum of all user wallet balances, and 5 most recent audit events."
    )
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getAdminDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getAdminDashboard()
        ));
    }
}
