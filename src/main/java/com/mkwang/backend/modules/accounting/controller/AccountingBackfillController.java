package com.mkwang.backend.modules.accounting.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.request.BackfillJournalBatchRequest;
import com.mkwang.backend.modules.accounting.dto.response.BackfillJournalPreviewResponse;
import com.mkwang.backend.modules.accounting.service.AccountingBackfillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/accountant/ledger/history") @RequiredArgsConstructor
public class AccountingBackfillController {
    private final AccountingBackfillService history;
    @GetMapping("/advances") public ApiResponse<?> advances(@RequestParam Long employeeId){return ApiResponse.success(history.employeeAdvances(employeeId));}
    @GetMapping("/candidates") public ApiResponse<?> candidates(@RequestParam(required=false)String search){return ApiResponse.success(history.candidates(search));}
    @PostMapping("/preview") public ResponseEntity<ApiResponse<BackfillJournalPreviewResponse>> preview(@Valid @RequestBody BackfillJournalBatchRequest request){
        return ResponseEntity.ok(ApiResponse.success(history.preview(request)));
    }
    @PostMapping("/commit") public ResponseEntity<ApiResponse<BackfillJournalPreviewResponse>> commit(@Valid @RequestBody BackfillJournalBatchRequest request){
        return ResponseEntity.ok(ApiResponse.success(history.commit(request)));
    }
}
