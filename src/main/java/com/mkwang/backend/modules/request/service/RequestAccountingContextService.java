package com.mkwang.backend.modules.request.service;

import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.request.dto.response.RequestAccountingContextResponse;
import com.mkwang.backend.modules.request.mapper.RequestAccountingMapper;
import com.mkwang.backend.modules.request.repository.AdvanceBalanceRepository;
import com.mkwang.backend.modules.request.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal read facade; callers must enforce their own source/role authorization. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestAccountingContextService {
    private final RequestRepository requests;
    private final AdvanceBalanceRepository advances;
    private final RequestAccountingMapper mapper;

    public RequestAccountingContextResponse forRequest(Long id) {
        return mapper.toContext(requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request", "id", id)));
    }

    public RequestAccountingContextResponse forAdvance(Long id) {
        return mapper.toContext(advances.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdvanceBalance", "id", id)).getAdvanceRequest());
    }
}
