package com.mkwang.backend.modules.accounting.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.response.CompanyFundResponse;
import com.mkwang.backend.modules.accounting.dto.response.ReconciliationReportResponse;
import com.mkwang.backend.modules.accounting.dto.request.SystemTopupRequest;
import com.mkwang.backend.modules.accounting.dto.request.UpdateBankStatementRequest;
import com.mkwang.backend.modules.accounting.service.CompanyFundService;
import com.mkwang.backend.modules.wallet.dto.response.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/company-fund")
@RequiredArgsConstructor
public class CompanyFundController {

    private final CompanyFundService companyFundService;

    /**
     * GET /api/v1/company-fund
     * Xem thông tin quỹ công ty: số dư ví nội bộ, số dư ngân hàng, chênh lệch.
     * Permission: COMPANY_FUND_VIEW (Accountant, CFO)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CompanyFundResponse>> getCompanyFund() {
        return ResponseEntity.ok(ApiResponse.success(companyFundService.getCompanyFund()));
    }

    /**
     * POST /api/v1/company-fund/topup
     * Nạp tiền từ ngân hàng vào quỹ công ty (SYSTEM_TOPUP).
     * Cập nhật Wallet(COMPANY_FUND) + FLOAT_MAIN.
     * Permission: COMPANY_FUND_TOPUP (Accountant, CFO)
     */
    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<TransactionResponse>> topup(
            @Valid @RequestBody SystemTopupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(companyFundService.topup(request)));
    }

    /**
     * PUT /api/v1/company-fund/bank-statement
     * Cập nhật số dư theo sao kê ngân hàng thực tế (nhập tay).
     * Dùng để tính bankDiscrepancy trong reconciliation report.
     * Permission: COMPANY_FUND_TOPUP (Accountant, CFO)
     */
    @PutMapping("/bank-statement")
    public ResponseEntity<ApiResponse<CompanyFundResponse>> updateBankStatement(
            @Valid @RequestBody UpdateBankStatementRequest request) {
        return ResponseEntity.ok(ApiResponse.success(companyFundService.updateBankStatement(request)));
    }

    /**
     * GET /api/v1/company-fund/reconciliation
     * Báo cáo đối soát đầy đủ: kiểm tra FLOAT_MAIN invariant + breakdown ví + so sánh ngân hàng.
     * Permission: COMPANY_FUND_VIEW (Accountant, CFO)
     */
    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<ReconciliationReportResponse>> getReconciliationReport() {
        return ResponseEntity.ok(ApiResponse.success(companyFundService.getReconciliationReport()));
    }
}
