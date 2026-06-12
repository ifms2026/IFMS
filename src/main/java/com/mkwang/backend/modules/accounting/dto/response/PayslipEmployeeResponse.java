package com.mkwang.backend.modules.accounting.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayslipEmployeeResponse {
    private Long id;
    private String fullName;
    private String employeeCode;
    private String departmentName;
    private String jobTitle;
    private String bankName;
    private String bankAccountNum;
}

