package com.mkwang.backend.modules.organization.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.organization.dto.request.CreateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.request.UpdateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.response.DepartmentDetailResponse;
import com.mkwang.backend.modules.organization.dto.response.DepartmentSummaryResponse;

import java.math.BigDecimal;

public interface DepartmentService {

    PageResponse<DepartmentSummaryResponse> getDepartments(String search, int page, int limit);

    DepartmentDetailResponse getDepartmentDetail(Long id);

    DepartmentDetailResponse createDepartment(CreateDepartmentRequest request);

    DepartmentDetailResponse updateDepartment(Long id, UpdateDepartmentRequest request);

    long countDepartments();

    /** CFO approved a DEPARTMENT_TOPUP: lifetime quota and available balance both increase. */
    void receiveQuota(Long departmentId, BigDecimal amount);

    /** Manager approved a PROJECT_TOPUP: available balance decreases, lifetime quota is unchanged. */
    void allocateToProject(Long departmentId, BigDecimal amount);

    /** Returns {totalProjectQuota, totalAvailableBalance} for the given department. */
    BigDecimal[] getBudgetSnapshot(Long departmentId);
}
