package com.mkwang.backend.modules.audit.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.audit.dto.response.AuditLogResponse;
import com.mkwang.backend.modules.audit.entity.AuditAction;
import com.mkwang.backend.modules.audit.entity.AuditLog;
import com.mkwang.backend.modules.audit.repository.AuditLogRepository;
import com.mkwang.backend.modules.audit.repository.AuditLogSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(
            Long actorId, AuditAction action, String entityName,
            LocalDate from, LocalDate to, int page, int size) {

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Specification<AuditLog> spec = AuditLogSpecification.filter(actorId, action, entityName, fromDt, toDt);

        Page<AuditLog> auditPage = auditLogRepository.findAll(spec, pageable);

        return PageResponse.<AuditLogResponse>builder()
                .items(auditPage.getContent().stream().map(AuditLogResponse::from).toList())
                .total(auditPage.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(auditPage.getTotalPages())
                .build();
    }
}
