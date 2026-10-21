package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.modules.request.service.RequestAccountingContextService;
import com.mkwang.backend.modules.request.dto.response.RequestAccountingContextResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@RequiredArgsConstructor
public class AccountingSourceService {
    private final RequestAccountingContextService contexts;
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public RequestAccountingContextResponse getRequest(Long id) { return contexts.forRequest(id); }
}
