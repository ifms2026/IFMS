package com.mkwang.backend.modules.profile.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyBankInfoResponse {

    private String bankName;
    private String accountNumber;
    private String accountOwner;
}

