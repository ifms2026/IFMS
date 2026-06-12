package com.mkwang.backend.modules.profile.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyProfileResponse {

    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String address;
    private Long departmentId;
    private String departmentName;
    private String jobTitle;
    private String citizenId;
    private String avatar;
    private BankInfoResponse bankInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankInfoResponse {
        private String bankName;
        private String accountNumber;
        private String accountOwner;
    }
}

