package com.mkwang.backend.modules.accounting.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.service.BankEvidenceService;
import com.mkwang.backend.modules.accounting.dto.request.ConfigureBankAccountRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/accountant/ledger/bank-evidence") @RequiredArgsConstructor
public class BankEvidenceController {
    private final BankEvidenceService bank;
    @GetMapping("/{id}/candidates") public ResponseEntity<ApiResponse<List<com.mkwang.backend.modules.wallet.dto.response.CorporateMovementResponse>>> candidates(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bank.candidates(id)));
    }
    @PostMapping("/{id}/match") public ResponseEntity<ApiResponse<String>> match(@PathVariable Long id,
            @Valid @RequestBody com.mkwang.backend.modules.accounting.dto.request.MatchBankStatementRequest request) {
        bank.confirmMatch(id,request.transactionId(),request.reason());
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận đối chiếu"));
    }
    @GetMapping public ResponseEntity<ApiResponse<List<Map<String,Object>>>> lines(
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(bank.lines(from,to,2000)));
    }
    @PostMapping("/account") public ResponseEntity<ApiResponse<String>> account(@Valid @RequestBody ConfigureBankAccountRequest request) {
        bank.configure(request);return ResponseEntity.ok(ApiResponse.success("Đã xác nhận phạm vi tài khoản ngân hàng"));
    }
}
