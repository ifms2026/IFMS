package com.mkwang.backend.modules.profile.mapper;

import com.mkwang.backend.modules.profile.dto.response.MyAvatarResponse;
import com.mkwang.backend.modules.profile.dto.response.MyBankInfoResponse;
import com.mkwang.backend.modules.profile.dto.response.MyProfileResponse;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

    public MyProfileResponse toMyProfileResponse(UserProfile profile) {
        User user = profile.getUser();

        return MyProfileResponse.builder()
                .id(user.getId())
                .employeeCode(profile.getEmployeeCode())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(profile.getPhoneNumber())
                .dateOfBirth(profile.getDateOfBirth())
                .address(profile.getAddress())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .jobTitle(profile.getJobTitle())
                .citizenId(profile.getCitizenId())
                .avatar(profile.getAvatarFile() != null ? profile.getAvatarFile().getUrl() : null)
                .bankInfo(MyProfileResponse.BankInfoResponse.builder()
                        .bankName(profile.getBankName())
                        .accountNumber(profile.getBankAccountNum())
                        .accountOwner(profile.getBankAccountOwner())
                        .build())
                .build();
    }

    public MyAvatarResponse toMyAvatarResponse(String avatarUrl) {
        return MyAvatarResponse.builder()
                .avatar(avatarUrl)
                .build();
    }

    public MyBankInfoResponse toMyBankInfoResponse(UserProfile profile) {
        return MyBankInfoResponse.builder()
                .bankName(profile.getBankName())
                .accountNumber(profile.getBankAccountNum())
                .accountOwner(profile.getBankAccountOwner())
                .build();
    }
}

