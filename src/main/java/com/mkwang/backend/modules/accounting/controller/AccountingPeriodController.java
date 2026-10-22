package com.mkwang.backend.modules.accounting.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.service.AccountingPeriodService;
import com.mkwang.backend.modules.accounting.dto.request.CloseAccountingPeriodRequest;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
@RestController
@RequestMapping("/accountant/ledger/periods")
@RequiredArgsConstructor
public class AccountingPeriodController {
    private final AccountingPeriodService periods;
    @GetMapping public ResponseEntity<ApiResponse<List<AccountingPeriodResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(periods.list()));
    }
    @GetMapping("/{period}/close-preview")
    public ResponseEntity<ApiResponse<com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodClosePreviewResponse>> previewClose(@PathVariable String period) {
        return ResponseEntity.ok(ApiResponse.success(periods.previewClose(period)));
    }
    @PostMapping("/{period}/close")
    public ResponseEntity<ApiResponse<String>> close(@PathVariable String period, @Valid @RequestBody CloseAccountingPeriodRequest request) {
        periods.close(period,request.reason(),request.reviewAcknowledged());
        return ResponseEntity.ok(ApiResponse.success("Đã đóng kỳ kế toán"));
    }
}
