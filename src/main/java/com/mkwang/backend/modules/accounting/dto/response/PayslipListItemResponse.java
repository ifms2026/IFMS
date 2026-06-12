package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PayslipListItemResponse {
    private Long id;
    private String payslipCode;
    private Long periodId;
    private String periodName;
    private Integer month;
    private Integer year;
    private PayslipStatus status;
    private BigDecimal finalNetSalary;
}

