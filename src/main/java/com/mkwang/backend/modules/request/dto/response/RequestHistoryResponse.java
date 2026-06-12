package com.mkwang.backend.modules.request.dto.response;

import com.mkwang.backend.modules.request.entity.RequestAction;
import com.mkwang.backend.modules.request.entity.RequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RequestHistoryResponse {
    private Long id;
    private RequestAction action;
    private RequestStatus statusAfterAction;
    private Long actorId;
    private String actorName;
    private String comment;
    private LocalDateTime createdAt;
}

