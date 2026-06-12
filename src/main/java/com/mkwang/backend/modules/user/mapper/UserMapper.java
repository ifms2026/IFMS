package com.mkwang.backend.modules.user.mapper;

import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.profile.entity.UserSecuritySettings;
import com.mkwang.backend.modules.user.dto.response.AdminUserDetailResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserSummaryResponse;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.wallet.entity.Wallet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class UserMapper {

    public AdminUserSummaryResponse toAdminSummary(User user) {
        UserProfile profile = user.getProfile();
        return AdminUserSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                .role(user.getRole().getName())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .avatar(profile != null && profile.getAvatarFile() != null ? profile.getAvatarFile().getUrl() : null)
                .debtBalance(BigDecimal.ZERO)
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public AdminUserDetailResponse toAdminDetail(User user, Wallet wallet, UserSecuritySettings security) {
        UserProfile profile = user.getProfile();

        AdminUserDetailResponse.WalletInfoResponse walletInfo = null;
        if (wallet != null) {
            walletInfo = AdminUserDetailResponse.WalletInfoResponse.builder()
                    .balance(wallet.getBalance())
                    .pendingBalance(wallet.getLockedBalance())
                    .debtBalance(BigDecimal.ZERO)
                    .build();
        }

        AdminUserDetailResponse.SecuritySettingsResponse securityInfo = null;
        if (security != null) {
            securityInfo = AdminUserDetailResponse.SecuritySettingsResponse.builder()
                    .hasPIN(security.getTransactionPin() != null && !security.getTransactionPin().isBlank())
                    .pinLockedUntil(security.getLockedUntil())
                    .retryCount(security.getRetryCount())
                    .build();
        }

        AdminUserDetailResponse.BankInfoResponse bankInfo = null;
        if (profile != null) {
            bankInfo = AdminUserDetailResponse.BankInfoResponse.builder()
                    .bankName(profile.getBankName())
                    .accountNumber(profile.getBankAccountNum())
                    .accountOwner(profile.getBankAccountOwner())
                    .build();
        }

        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                .role(user.getRole().getName())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .phoneNumber(profile != null ? profile.getPhoneNumber() : null)
                .dateOfBirth(profile != null ? profile.getDateOfBirth() : null)
                .citizenId(profile != null ? profile.getCitizenId() : null)
                .address(profile != null ? profile.getAddress() : null)
                .avatar(profile != null && profile.getAvatarFile() != null ? profile.getAvatarFile().getUrl() : null)
                .status(user.getStatus().name())
                .isFirstLogin(user.getIsFirstLogin())
                .bankInfo(bankInfo)
                .wallet(walletInfo)
                .securitySettings(securityInfo)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
