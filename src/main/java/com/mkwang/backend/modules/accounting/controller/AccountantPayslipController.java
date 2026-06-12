package com.mkwang.backend.modules.accounting.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipDetailResponse;
import com.mkwang.backend.modules.accounting.service.PayslipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accountant/payslips")
@RequiredArgsConstructor
@Tag(name = "Accountant - Payslips", description = "Payslip lookup for accountants")
@SecurityRequirement(name = "bearerAuth")
public class AccountantPayslipController {

    private final PayslipService payslipService;

    @GetMapping("/{payslipId}")
    @Operation(summary = "Get payslip detail",
               description = "Full detail of a single payslip by ID. " +
                             "Typically used to look up a payslip from a ledger entry referenceId.")
    public ResponseEntity<ApiResponse<PayslipDetailResponse>> getPayslipDetail(
            @PathVariable Long payslipId) {

        return ResponseEntity.ok(ApiResponse.success(
                payslipService.getPayslipById(payslipId)));
    }
}
