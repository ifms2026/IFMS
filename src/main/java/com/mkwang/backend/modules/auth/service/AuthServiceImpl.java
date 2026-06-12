package com.mkwang.backend.modules.auth.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.modules.auth.dto.request.*;
import com.mkwang.backend.modules.auth.dto.response.AuthenticationResponse;
import com.mkwang.backend.modules.auth.dto.response.UserInfoResponse;
import com.mkwang.backend.modules.auth.security.JwtService;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.file.service.FileStorageService;
import com.mkwang.backend.modules.mail.publisher.MailPublisher;
import com.mkwang.backend.modules.mail.publisher.MailType;
import com.mkwang.backend.modules.profile.service.ProfileService;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.mkwang.backend.modules.audit.context.AuditContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom;
    private final MailPublisher mailPublisher;

    private static final String PASSWORD_RESET_PREFIX    = "password_reset_email: ";
    private static final String FIRST_LOGIN_SETUP_PREFIX = "first_login_setup:";



    @Value("${app.otp.ttl}")
    private long otpExpiration;

    @Value("${app.otp.length}")
    private int otpLength;

    @Value("${app.auth.setup-token-ttl-minutes}")
    private long setupTokenTtlMinutes;

    // ── POST /auth/login ─────────────────────────────────────────

    @Override
    @Transactional
    public AuthenticationResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()));

        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Cập nhật actorId vào AuditContext — vì đây là public endpoint,
        // JwtFilter skip nên AuditContextFilter không đọc được actorId.
        // Phải set thủ công sau khi authenticationManager xác thực thành công.
        AuditContextHolder.setActorId(user.getId());

        // First-login gate: ADMIN được vào thẳng, các role nghiệp vụ phải đổi mật khẩu + đặt PIN.
        if (requiresFirstLoginSetup(user)) {
            String setupToken = UUID.randomUUID().toString();
            stringRedisTemplate.opsForValue().set(
                    FIRST_LOGIN_SETUP_PREFIX + setupToken,
                    String.valueOf(user.getId()),
                    setupTokenTtlMinutes,
                    TimeUnit.MINUTES);
            log.info("First-login setup token issued for user: {}", user.getEmail());
            return AuthenticationResponse.builder()
                    .requiresSetup(true)
                    .setupToken(setupToken)
                    .user(buildUserInfo(user))
                    .build();
        }

        // Single-session: tăng tokenVersion → invalidate mọi token cũ
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Cache version mới vào Redis
        jwtService.cacheTokenVersion(user.getId(), user.getTokenVersion());

        log.info("User logged in: {}", user.getEmail());
        return generateTokenResponse(user);
    }

    // ── POST /auth/refresh-token ─────────────────────────────────

    @Override
    public AuthenticationResponse refreshToken(String refreshToken) {
        String userEmail;
        try {
            userEmail = jwtService.extractUsername(refreshToken);
        } catch (Exception e) {
            log.warn("Invalid refresh token format: {}", e.getMessage());
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (userEmail == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        UserDetailsAdapter userDetails = new UserDetailsAdapter(user);

        // Stateless: chỉ check chữ ký + expiry + version
        if (!jwtService.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (!jwtService.isTokenVersionValid(refreshToken, user.getId())) {
            throw new UnauthorizedException("Session expired. Please login again.");
        }

        log.info("Token refreshed for user: {}", user.getEmail());
        return generateTokenResponse(user); // giữ tokenVersion hiện tại
    }

    // ── POST /auth/logout ────────────────────────────────────────

    @Override
    @Transactional
    public void logout(String accessToken) {
        try {
            String userEmail = jwtService.extractUsername(accessToken);
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            // Cập nhật actorId — logout có JWT nhưng không qua JwtFilter bình thường
            AuditContextHolder.setActorId(user.getId());

            // Stateless logout: tăng tokenVersion → mọi token hiện tại đều bị invalidate
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
            jwtService.cacheTokenVersion(user.getId(), user.getTokenVersion());

            log.info("User logged out: {}", userEmail);
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            throw new UnauthorizedException("Invalid token");
        }
    }

    // ── POST /auth/forgot-password ───────────────────────────────

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        if(!userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceNotFoundException("Email not found");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        String key = PASSWORD_RESET_PREFIX + request.getEmail();
        String otp = generateDigitString(otpLength);

        ForgotPasswordOtpData data = ForgotPasswordOtpData.builder()
                .email(request.getEmail())
                .newPassword(request.getNewPassword())
                .otp(otp)
                .build();

        redisTemplate.opsForValue().set(key, data,otpExpiration
        , TimeUnit.MILLISECONDS);
        mailPublisher.publish(MailType.FORGET_PASSWORD, request.getEmail(), "Password Reset OTP",
                String.format("Your OTP for password reset is: %s. It expires in %d minutes.",
                        otp, TimeUnit.MILLISECONDS.toMinutes(otpExpiration)));
    }

// ── POST /auth/verify-password-reset-otp ─────────────────────

    @Override
    @Transactional // Nên có Transactional vì hàm này có gọi userRepository.save()
    public void verifyPasswordResetOtp(VerifyOtpPasswordResetRequest request) {
        String key = PASSWORD_RESET_PREFIX + request.email();
        ForgotPasswordOtpData data = (ForgotPasswordOtpData) redisTemplate.opsForValue().get(key);

        // 1. Kiểm tra Null (OTP hết hạn hoặc email không tồn tại trong cache)
        if (data == null) {
            throw new BadRequestException("OTP has expired or is invalid");
        }

        // 2. Kiểm tra OTP khớp không
        if(!request.otp().equals(data.getOtp())) {
            throw new BadRequestException("Invalid OTP");
        }

        // 3. Tiến hành đổi mật khẩu
        var user = userRepository.findByEmail(data.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(data.getNewPassword()));

        // Đổi mật khẩu thì nên tăng tokenVersion để kick user ra khỏi các thiết bị khác (Single-session)
        user.setTokenVersion(user.getTokenVersion() + 1);

        userRepository.save(user);

        // Cập nhật lại tokenVersion lên Redis
        jwtService.cacheTokenVersion(user.getId(), user.getTokenVersion());

        // 4. BẮT BUỘC: Xóa OTP khỏi Redis để tránh bị dùng lại (Replay Attack)
        redisTemplate.delete(key);
    }


    // ── POST /auth/change-password ───────────────────────────────

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, String username) {
        var user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        log.info("Changing password for user: {}", username);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new BadRequestException("New passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ── POST /auth/first-login/complete ─────────────────────────

    @Override
    @Transactional
    public AuthenticationResponse firstLoginSetup(FirstLoginSetupRequest request) {
        // 1. Validate setup token
        String redisKey = FIRST_LOGIN_SETUP_PREFIX + request.getSetupToken();
        String userIdRaw = stringRedisTemplate.opsForValue().get(redisKey);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BadRequestException("Setup token has expired or is invalid");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdRaw);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Setup token has expired or is invalid");
        }

        // 2. Load user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getIsFirstLogin())) {
            throw new BadRequestException("Account setup has already been completed");
        }

        // 3. Validate passwords
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        // 4. Change password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // 5. Set transaction PIN (validate + hash in ProfileService)
        profileService.createSecuritySettings(user, request.getPin());

        // 6. Mark first-login complete + bump tokenVersion (single-session)
        user.setIsFirstLogin(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // 7. Invalidate setup token — one-time use
        stringRedisTemplate.delete(redisKey);

        // 8. Cache new tokenVersion and issue full tokens
        jwtService.cacheTokenVersion(user.getId(), user.getTokenVersion());
        AuditContextHolder.setActorId(user.getId());

        log.info("First-login setup completed for user: {}", user.getEmail());
        return generateTokenResponse(user);
    }

    // ── GET /auth/me ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(String username) {
        var user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return buildUserInfo(user);
    }

    // ── Private helpers ──────────────────────────────────────────

    private AuthenticationResponse generateTokenResponse(User user) {
        UserDetailsAdapter userDetails = new UserDetailsAdapter(user);
        int version = user.getTokenVersion();

        var accessToken = jwtService.generateToken(userDetails, version);
        var refreshToken = jwtService.generateRefreshToken(userDetails, version);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(buildUserInfo(user))
                .build();
    }

    private boolean requiresFirstLoginSetup(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : null;
        return Boolean.TRUE.equals(user.getIsFirstLogin()) && !"ADMIN".equals(roleName);
    }

    private UserInfoResponse buildUserInfo(User user) {

        var avatar = profileService.getProfileByUserId(user.getId()).getAvatarFile(); // ensure profile is loaded for serialization (avatar)
        return UserInfoResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .avatar(avatar != null ? fileStorageService.getFile(avatar.getId()).getUrl() : null)
                .isFirstLogin(user.getIsFirstLogin())
                .status(user.getStatus().name())
                .build();
    }

    private String generateDigitString(int n) {
        StringBuilder otp = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }
}
