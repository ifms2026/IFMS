package com.mkwang.backend.modules.audit.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.audit.dto.response.AuditLogResponse;
import com.mkwang.backend.modules.audit.entity.AuditAction;

import java.time.LocalDate;

public interface AuditLogService {

    PageResponse<AuditLogResponse> getAuditLogs(
            Long actorId,
            AuditAction action,
            String entityName,
            LocalDate from,
            LocalDate to,
            int page,
            int size);
}
