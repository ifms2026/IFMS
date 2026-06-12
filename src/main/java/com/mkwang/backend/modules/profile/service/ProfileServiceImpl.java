package com.mkwang.backend.modules.profile.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.LockedException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.common.utils.PinValidator;
import com.mkwang.backend.modules.file.dto.request.FileStorageRequest;
import com.mkwang.backend.modules.file.entity.FileStorage;
import com.mkwang.backend.modules.file.service.FileStorageService;
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
import com.mkwang.backend.modules.profile.mapper.ProfileMapper;
import com.mkwang.backend.modules.profile.repository.UserProfileRepository;
import com.mkwang.backend.modules.profile.repository.UserSecuritySettingsRepository;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final List<BankOptionResponse> SUPPORTED_BANKS = List.of(
            BankOptionResponse.builder().value("MB Bank").label("MB Bank (Quan doi)").build(),
            BankOptionResponse.builder().value("Vietcombank").label("Vietcombank (VCB)").build(),
            BankOptionResponse.builder().value("Techcombank").label("Techcombank (TCB)").build(),
            BankOptionResponse.builder().value("BIDV").label("BIDV").build(),
            BankOptionResponse.builder().value("VietinBank").label("VietinBank").build(),
            BankOptionResponse.builder().value("ACB").label("ACB (A Chau)").build(),
            BankOptionResponse.builder().value("VPBank").label("VPBank (Viet Nam Thinh Vuong)").build(),
            BankOptionResponse.builder().value("TPBank").label("TPBank (Tien Phong)").build(),
            BankOptionResponse.builder().value("Sacombank").label("Sacombank").build(),
            BankOptionResponse.builder().value("HDBank").label("HDBank (Phat trien TP.HCM)").build()
    );

    private final UserProfileRepository userProfileRepository;
    private final UserSecuritySettingsRepository userSecuritySettingsRepository;
    private final FileStorageService fileStorageService;
    private final ProfileMapper profileMapper;
    private final PasswordEncoder passwordEncoder;
    private final PinValidator pinValidator;

    @Override
    @Transactional
    public UserProfile createProfile(User user, String employeeCode, String jobTitle, String phoneNumber) {
        UserProfile profile = UserProfile.builder()
                .user(user)
                .employeeCode(employeeCode)
                .jobTitle(jobTitle)
                .phoneNumber(phoneNumber)
                .build();
        return userProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public UserSecuritySettings createSecuritySettings(User user, String pin) {
        pinValidator.validate(pin);

        UserSecuritySettings settings = userSecuritySettingsRepository
                .findById(user.getId())
                .orElse(UserSecuritySettings.builder().user(user).build());
        settings.setTransactionPin(passwordEncoder.encode(pin));
        settings.resetRetryCount();
        return userSecuritySettingsRepository.save(settings);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getProfileByUserId(Long userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", "userId", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        UserProfile profile = getProfileByUserId(userId);
        return profileMapper.toMyProfileResponse(profile);
    }

    @Override
    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, UpdateMyProfileRequest request) {
        UserProfile profile = getProfileByUserId(userId);
        String normalizedPhoneNumber = request.getPhoneNumber().trim();

        if (userProfileRepository.existsByPhoneNumberAndUserIdNot(normalizedPhoneNumber, userId)) {
            throw new BadRequestException("Phone number already exists: " + normalizedPhoneNumber);
        }

        profile.getUser().setFullName(request.getFullName().trim());
        profile.setPhoneNumber(normalizedPhoneNumber);
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setCitizenId(normalizeOptionalField(request.getCitizenId()));
        profile.setAddress(normalizeOptionalField(request.getAddress()));

        UserProfile savedProfile = userProfileRepository.save(profile);
        return profileMapper.toMyProfileResponse(savedProfile);
    }

    @Override
    @Transactional
    public MyAvatarResponse updateMyAvatar(Long userId, UpdateMyAvatarRequest request) {
        UserProfile profile = getProfileByUserId(userId);
        FileStorage previousAvatar = profile.getAvatarFile();

        FileStorageRequest fileStorageRequest = new FileStorageRequest();
        fileStorageRequest.setFileName(request.getFileName().trim());
        fileStorageRequest.setCloudinaryPublicId(request.getCloudinaryPublicId().trim());
        fileStorageRequest.setUrl(request.getUrl().trim());
        fileStorageRequest.setFileType(normalizeOptionalField(request.getFileType()));
        fileStorageRequest.setSize(request.getSize());

        FileStorage savedFile = fileStorageService.save(fileStorageRequest);
        profile.setAvatarFile(savedFile);
        userProfileRepository.save(profile);

        if (previousAvatar != null && !previousAvatar.getId().equals(savedFile.getId())) {
            boolean oldAvatarDeleted = fileStorageService.deleteFileBestEffort(previousAvatar.getId());
            if (!oldAvatarDeleted) {
                log.warn(
                        "Avatar updated for userId={}, but old avatar cleanup was deferred. oldFileId={}, newFileId={}",
                        userId,
                        previousAvatar.getId(),
                        savedFile.getId()
                );
            }
        }

        return profileMapper.toMyAvatarResponse(savedFile.getUrl());
    }

    @Override
    @Transactional
    public MyBankInfoResponse updateMyBankInfo(Long userId, UpdateMyBankInfoRequest request) {
        UserProfile profile = getProfileByUserId(userId);

        profile.setBankName(request.getBankName().trim());
        profile.setBankAccountNum(request.getAccountNumber().trim());
        profile.setBankAccountOwner(request.getAccountOwner().trim());

        UserProfile savedProfile = userProfileRepository.save(profile);
        return profileMapper.toMyBankInfoResponse(savedProfile);
    }

    @Override
    @Transactional
    public PinMessageResponse updateMyPin(Long userId, UpdateMyPinRequest request) {
        if (!pinValidator.isValidFormat(request.getCurrentPin())) {
            throw new BadRequestException("currentPin must be exactly " + pinValidator.getPinLength() + " digits");
        }

        pinValidator.validate(request.getNewPin());
        if (pinValidator.isSamePin(request.getCurrentPin(), request.getNewPin())) {
            throw new BadRequestException("newPin must be different from currentPin");
        }

        UserSecuritySettings settings = getSecuritySettings(userId);
        if (settings.isPinLocked()) {
            throw new LockedException("PIN is temporarily locked. Please try again later");
        }

        if (!passwordEncoder.matches(request.getCurrentPin(), settings.getTransactionPin())) {
            throw new UnauthorizedException("Current PIN is incorrect");
        }

        settings.setTransactionPin(passwordEncoder.encode(request.getNewPin()));
        settings.resetRetryCount();
        userSecuritySettingsRepository.save(settings);

        return PinMessageResponse.builder()
                .message("PIN updated successfully")
                .build();
    }

    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, LockedException.class})
    public PinVerifyResponse verifyMyPin(Long userId, VerifyMyPinRequest request) {
        if (!pinValidator.isValidFormat(request.getPin())) {
            throw new UnauthorizedException("Invalid PIN");
        }

        UserSecuritySettings settings = getSecuritySettings(userId);
        if (settings.isPinLocked()) {
            throw new LockedException("PIN is temporarily locked. Please try again later");
        }

        if (!passwordEncoder.matches(request.getPin(), settings.getTransactionPin())) {
            settings.incrementRetryCount();
            if (pinValidator.shouldLock(settings.getRetryCount())) {
                settings.lockPin(pinValidator.getLockMinutes());
                userSecuritySettingsRepository.save(settings);
                throw new LockedException("PIN has been locked due to too many failed attempts");
            }
            userSecuritySettingsRepository.save(settings);
            int remaining = pinValidator.getMaxRetry() - settings.getRetryCount();
            return PinVerifyResponse.builder()
                    .valid(false)
                    .attemptsRemaining(remaining)
                    .build();
        }

        settings.resetRetryCount();
        userSecuritySettingsRepository.save(settings);
        return PinVerifyResponse.builder().valid(true).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankOptionResponse> getSupportedBanks() {
        return SUPPORTED_BANKS;
    }

    private String normalizeOptionalField(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserSecuritySettings getSecuritySettings(Long userId) {
        UserSecuritySettings settings = userSecuritySettingsRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserSecuritySettings", "userId", userId));

        if (settings.getTransactionPin() == null || settings.getTransactionPin().isBlank()) {
            throw new BadRequestException("Transaction PIN has not been set up yet");
        }
        return settings;
    }
}
