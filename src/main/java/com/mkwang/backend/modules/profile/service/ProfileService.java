package com.mkwang.backend.modules.profile.service;

import com.mkwang.backend.modules.profile.dto.request.UpdateMyProfileRequest;
import com.mkwang.backend.modules.profile.dto.request.UpdateMyAvatarRequest;
import com.mkwang.backend.modules.profile.dto.request.UpdateMyBankInfoRequest;
import com.mkwang.backend.modules.profile.dto.request.UpdateMyPinRequest;
import com.mkwang.backend.modules.profile.dto.request.VerifyMyPinRequest;
import com.mkwang.backend.modules.profile.dto.response.BankOptionResponse;
import com.mkwang.backend.modules.profile.dto.response.MyAvatarResponse;
import com.mkwang.backend.modules.profile.dto.response.MyBankInfoResponse;
import com.mkwang.backend.modules.profile.dto.response.MyProfileResponse;
import com.mkwang.backend.modules.profile.dto.response.PinMessageResponse;
import com.mkwang.backend.modules.profile.dto.response.PinVerifyResponse;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.profile.entity.UserSecuritySettings;
import com.mkwang.backend.modules.user.entity.User;

import java.util.List;

public interface ProfileService {

    /**
     * Tạo UserProfile cho user mới trong quá trình onboarding.
     *
     * @param user           user đã được persisted
     * @param employeeCode   mã nhân viên (VD: MK011)
     * @param jobTitle       chức danh
     * @param phoneNumber    số điện thoại
     * @return UserProfile đã được lưu
     */
    UserProfile createProfile(User user, String employeeCode, String jobTitle, String phoneNumber);

    /**
     * Tạo hoặc cập nhật UserSecuritySettings với transaction PIN đã validate + hash.
     * Được gọi khi user hoàn tất first-login setup.
     *
     * @param user user cần thiết lập PIN
     * @param pin  PIN raw từ request
     * @return UserSecuritySettings đã được lưu
     */
    UserSecuritySettings createSecuritySettings(User user, String pin);

    /**
     * Lấy UserProfile theo userId.
     * Dùng để đọc thông tin ngân hàng (bankAccountNum, bankAccountOwner, bankName)
     * khi user tạo WithdrawRequest.
     *
     * @param userId ID of the user
     * @return UserProfile
     * @throws com.mkwang.backend.common.exception.ResourceNotFoundException nếu không tồn tại
     */
    UserProfile getProfileByUserId(Long userId);

    MyProfileResponse getMyProfile(Long userId);

    MyProfileResponse updateMyProfile(Long userId, UpdateMyProfileRequest request);

    MyAvatarResponse updateMyAvatar(Long userId, UpdateMyAvatarRequest request);

    MyBankInfoResponse updateMyBankInfo(Long userId, UpdateMyBankInfoRequest request);

    PinMessageResponse updateMyPin(Long userId, UpdateMyPinRequest request);

    PinVerifyResponse verifyMyPin(Long userId, VerifyMyPinRequest request);

    List<BankOptionResponse> getSupportedBanks();
}
