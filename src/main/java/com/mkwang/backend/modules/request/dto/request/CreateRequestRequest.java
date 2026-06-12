package com.mkwang.backend.modules.request.dto.request;

import com.mkwang.backend.modules.request.entity.RequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateRequestRequest {

    @NotNull
    private RequestType type;

    private Long projectId;
    private Long phaseId;
    private Long categoryId;
    private Long advanceBalanceId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String description;

    @Valid
    private List<AttachmentRequest> attachments = new ArrayList<>();
}

