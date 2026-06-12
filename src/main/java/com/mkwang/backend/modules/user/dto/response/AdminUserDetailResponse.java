package com.mkwang.backend.modules.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {

    private Long id;
    private String fullName;
    private String email;
    private String employeeCode;
    private String role;
    private Long departmentId;
    private String departmentName;
    private String jobTitle;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String citizenId;
    private String address;
    private String avatar;
    private String status;
    private Boolean isFirstLogin;
    private BankInfoResponse bankInfo;
    private WalletInfoResponse wallet;
    private SecuritySettingsResponse securitySettings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankInfoResponse {
        private String bankName;
        private String accountNumber;
        private String accountOwner;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletInfoResponse {
        private BigDecimal balance;
        private BigDecimal pendingBalance;
        private BigDecimal debtBalance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecuritySettingsResponse {
        private Boolean hasPIN;
        private LocalDateTime pinLockedUntil;
        private Integer retryCount;
    }
}
