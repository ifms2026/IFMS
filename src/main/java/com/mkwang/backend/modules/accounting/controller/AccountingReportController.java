package com.mkwang.backend.modules.accounting.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountLedgerResponse;
import com.mkwang.backend.modules.accounting.service.AccountingReportService;
import com.mkwang.backend.modules.request.dto.response.RequestAccountingContextResponse;
import com.mkwang.backend.modules.accounting.service.AccountingSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/accountant/ledger")
@RequiredArgsConstructor
public class AccountingReportController {
    private final AccountingReportService reports;
    private final AccountingSourceService sources;
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<AccountLedgerResponse>> accounts(
            @RequestParam(required = false) String account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId, @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long phaseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(reports.getLedger(account, from, to, employeeId, projectId,
                departmentId, phaseId, categoryId, page, limit)));
    }
    @GetMapping("/sources/requests/{id}")
    public ResponseEntity<ApiResponse<RequestAccountingContextResponse>> source(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(sources.getRequest(id)));
    }
}
