package com.mkwang.backend.modules.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardUserResponse {

    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private String role;
    private String departmentName;

    /**
     * Mật khẩu tạm thời — chỉ trả về một lần duy nhất trong response này.
     * Đồng thời được gửi qua email onboard.
     */
    private String temporaryPassword;
}
