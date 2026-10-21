package com.mkwang.backend.modules.accounting.dto.response;
import java.time.Instant;
public record AccountingPeriodResponse(String period, boolean closed, Instant closedAt, Long closedBy, String reason) {}
