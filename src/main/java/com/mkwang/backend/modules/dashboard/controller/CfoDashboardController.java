package com.mkwang.backend.modules.dashboard.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CfoDashboardResponse;
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
@RequestMapping("/cfo")
@RequiredArgsConstructor
@Tag(name = "CFO", description = "CFO dashboard and approvals")
@SecurityRequirement(name = "bearerAuth")
public class CfoDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    @Operation(
        summary = "CFO dashboard snapshot",
        description = "Returns company fund balance, pending DEPARTMENT_TOPUP count, monthly approved/rejected stats, and 5 most recent department topup requests."
    )
    public ResponseEntity<ApiResponse<CfoDashboardResponse>> getCfoDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getCfoDashboard()
        ));
    }
}
