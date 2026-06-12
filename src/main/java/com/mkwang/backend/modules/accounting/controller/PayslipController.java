package com.mkwang.backend.modules.accounting.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipListItemResponse;
import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import com.mkwang.backend.modules.accounting.service.PayslipService;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payslips")
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService payslipService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PayslipListItemResponse>>> getMyPayslips(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) PayslipStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int limit) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(
                payslipService.getMyPayslips(userId, year, status, page, limit)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayslipDetailResponse>> getMyPayslipDetail(
            @AuthenticationPrincipal UserDetailsAdapter userDetails,
            @PathVariable("id") Long payslipId) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(
                payslipService.getMyPayslipById(userId, payslipId)
        ));
    }
}

