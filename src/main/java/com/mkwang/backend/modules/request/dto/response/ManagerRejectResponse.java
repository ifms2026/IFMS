package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ManagerRejectResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private String rejectReason;
}

