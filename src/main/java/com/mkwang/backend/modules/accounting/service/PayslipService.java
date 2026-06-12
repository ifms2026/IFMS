package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipListItemResponse;
import com.mkwang.backend.modules.accounting.entity.PayslipStatus;

public interface PayslipService {

    PageResponse<PayslipListItemResponse> getMyPayslips(Long userId, Integer year, PayslipStatus status, int page, int limit);

    PayslipDetailResponse getMyPayslipById(Long userId, Long payslipId);

    PayslipDetailResponse getPayslipById(Long payslipId);
}

