package com.mkwang.backend.modules.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordOtpData {


    private String email;

    private String newPassword;

    private String otp;
}
