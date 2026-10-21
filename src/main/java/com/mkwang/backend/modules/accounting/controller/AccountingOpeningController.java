package com.mkwang.backend.modules.accounting.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.accounting.dto.request.OpeningBalanceRequest;
import com.mkwang.backend.modules.accounting.service.AccountingOpeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/accountant/ledger/opening") @RequiredArgsConstructor
public class AccountingOpeningController {
    private final AccountingOpeningService service;
    @GetMapping("/scopes") public ApiResponse<?> scopes(){return ApiResponse.success(service.scopes());}
    @GetMapping("/preview") public ApiResponse<?> preview(@RequestParam String kind,@RequestParam Long sourceId){return ApiResponse.success(service.preview(kind,sourceId));}
    @PostMapping public ApiResponse<?> commit(@Valid @RequestBody OpeningBalanceRequest input){return ApiResponse.success(service.commit(input));}
}
