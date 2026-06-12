package com.mkwang.backend.modules.organization.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.organization.dto.request.CreateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.request.UpdateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.response.DepartmentDetailResponse;
import com.mkwang.backend.modules.organization.dto.response.DepartmentSummaryResponse;

public interface DepartmentService {

    PageResponse<DepartmentSummaryResponse> getDepartments(String search, int page, int limit);

    DepartmentDetailResponse getDepartmentDetail(Long id);

    DepartmentDetailResponse createDepartment(CreateDepartmentRequest request);

    DepartmentDetailResponse updateDepartment(Long id, UpdateDepartmentRequest request);

    long countDepartments();
}
