package com.mkwang.backend.modules.accounting.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.request.ReverseAccountingJournalRequest;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.service.AccountingCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
@RestController @RequestMapping("/accountant/ledger/journals") @RequiredArgsConstructor
public class AccountingCorrectionController {
    private final AccountingCorrectionService corrections;
    private final com.mkwang.backend.modules.accounting.service.PayrollReplacementService payroll;
    @PostMapping("/{id}/replace-payslip") public ResponseEntity<ApiResponse<Long>> replace(
            @PathVariable Long id,@Valid @RequestBody com.mkwang.backend.modules.accounting.dto.request.ReplacePayslipRequest request){
        return ResponseEntity.ok(ApiResponse.success(payroll.replace(id,request)));
    }
    @PostMapping("/{id}/reverse") public ResponseEntity<ApiResponse<List<AccountingJournalDetailResponse>>> reverse(
            @PathVariable Long id,@Valid @RequestBody ReverseAccountingJournalRequest request){
        return ResponseEntity.ok(ApiResponse.success(corrections.reverse(id,request)));
    }
}
