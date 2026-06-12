package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PayslipDetailResponse {
    private Long id;
    private String payslipCode;
    private Long periodId;
    private String periodCode;
    private String periodName;
    private Integer month;
    private Integer year;
    private PayslipStatus status;

    private BigDecimal baseSalary;
    private BigDecimal bonus;
    private BigDecimal allowance;
    private BigDecimal totalEarnings;

    private BigDecimal deduction;
    private BigDecimal advanceDeduct;
    private BigDecimal totalDeduction;

    private BigDecimal finalNetSalary;
    private PayslipEmployeeResponse employee;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

