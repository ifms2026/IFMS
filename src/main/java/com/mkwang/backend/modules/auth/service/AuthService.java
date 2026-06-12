package com.mkwang.backend.modules.auth.service;

import com.mkwang.backend.modules.auth.dto.request.*;
import com.mkwang.backend.modules.auth.dto.response.AuthenticationResponse;
import com.mkwang.backend.modules.auth.dto.response.UserInfoResponse;

public interface AuthService {

    AuthenticationResponse login(LoginRequest request);

    AuthenticationResponse firstLoginSetup(FirstLoginSetupRequest request);

    AuthenticationResponse refreshToken(String refreshToken);

    void logout(String accessToken);

    void forgotPassword(ForgotPasswordRequest request);

    void verifyPasswordResetOtp(VerifyOtpPasswordResetRequest request);

    void changePassword(ChangePasswordRequest request, String username);

    UserInfoResponse getCurrentUser(String username);
}
