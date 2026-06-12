package com.mkwang.backend.modules.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSummaryResponse {

    private Long id;
    private String fullName;
    private String email;
    private String employeeCode;
    private String role;
    private Long departmentId;
    private String departmentName;
    private String jobTitle;
    private String avatar;
    private BigDecimal debtBalance;
    private String status;
    private LocalDateTime createdAt;
}
