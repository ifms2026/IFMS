package com.mkwang.backend.modules.accounting.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.request.ReconcileCostCountersRequest;
import com.mkwang.backend.modules.accounting.service.AccountingCostReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/accountant/ledger/cost-reconciliation") @RequiredArgsConstructor
public class AccountingCostReconciliationController {
    private final AccountingCostReconciliationService service;
    @GetMapping("/{projectId}") public ApiResponse<?> preview(@PathVariable Long projectId){return ApiResponse.success(service.preview(projectId));}
    @PostMapping public ApiResponse<?> apply(@Valid @RequestBody ReconcileCostCountersRequest request){return ApiResponse.success(service.apply(request));}
}
