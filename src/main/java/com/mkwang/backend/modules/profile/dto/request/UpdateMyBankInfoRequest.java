package com.mkwang.backend.modules.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyBankInfoRequest {

    @NotBlank(message = "bankName is required")
    @Size(max = 100, message = "bankName must be at most 100 characters")
    private String bankName;

    @NotBlank(message = "accountNumber is required")
    @Size(max = 30, message = "accountNumber must be at most 30 characters")
    private String accountNumber;

    @NotBlank(message = "accountOwner is required")
    @Size(max = 100, message = "accountOwner must be at most 100 characters")
    private String accountOwner;
}

