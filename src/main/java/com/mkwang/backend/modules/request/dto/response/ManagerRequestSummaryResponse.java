package com.mkwang.backend.modules.request.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ManagerRequestSummaryResponse {
    private long totalPendingCfoApproval;
    private long totalApproved;
    private long totalRejected;
    private long totalPaid;
    private long totalCancelled;
}

