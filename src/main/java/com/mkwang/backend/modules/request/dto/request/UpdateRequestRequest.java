package com.mkwang.backend.modules.request.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class UpdateRequestRequest {

    @Positive
    private BigDecimal amount;

    private String description;

    @Valid
    private List<AttachmentRequest> attachments;
}

