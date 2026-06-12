package com.mkwang.backend.modules.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyProfileRequest {

    @NotBlank(message = "fullName is required")
    @Size(max = 255, message = "fullName must be at most 255 characters")
    private String fullName;

    @NotBlank(message = "phoneNumber is required")
    @Size(max = 15, message = "phoneNumber must be at most 15 characters")
    private String phoneNumber;

    private LocalDate dateOfBirth;

    @Size(max = 20, message = "citizenId must be at most 20 characters")
    private String citizenId;

    @Size(max = 255, message = "address must be at most 255 characters")
    private String address;
}

