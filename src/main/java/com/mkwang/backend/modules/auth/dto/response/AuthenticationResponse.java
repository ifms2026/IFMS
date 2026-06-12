package com.mkwang.backend.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {

    private String accessToken;

    private String refreshToken;

    private UserInfoResponse user;

    /**
     * true khi user đăng nhập lần đầu — chưa phát access/refresh token.
     * Frontend phải redirect tới trang setup (đổi mật khẩu + đặt PIN).
     */
    private Boolean requiresSetup;

    /**
     * Short-lived token (15 phút, one-time use) để xác thực POST /auth/first-login/complete.
     * Chỉ có mặt khi requiresSetup = true.
     */
    private String setupToken;
}
