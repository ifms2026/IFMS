package com.mkwang.backend.modules.request.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRequestRequest {

    private String comment;

    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal approvedAmount;
}

