package com.mkwang.backend.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User info theo API Spec — dùng trong login response và GET /auth/me.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoResponse {

    private Long id;

    private String fullName;

    private String email;

    private String role;

    private Long departmentId;

    private String departmentName;

    private String avatar;

    private Boolean isFirstLogin;

    private String status;
}
