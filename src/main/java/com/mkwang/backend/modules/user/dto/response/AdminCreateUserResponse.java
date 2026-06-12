package com.mkwang.backend.modules.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String role;
    private Long departmentId;
    private String departmentName;
    private String status;
    private Boolean isFirstLogin;
    private LocalDateTime createdAt;
}
