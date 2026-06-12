package com.mkwang.backend.modules.request.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamLeaderRequestSummaryResponse {
    private long totalPendingManagerApproval;
    private long totalApproved;
    private long totalRejected;
    private long totalPaid;
    private long totalCancelled;
}

