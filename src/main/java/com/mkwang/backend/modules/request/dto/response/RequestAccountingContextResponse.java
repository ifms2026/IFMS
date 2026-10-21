package com.mkwang.backend.modules.request.dto.response;

import java.time.LocalDate;
import java.util.List;

public record RequestAccountingContextResponse(
        Long requestId, String requestCode, String type, String status, LocalDate businessDate,
        Long departmentId, String departmentName, Long projectId, String projectName,
        Long phaseId, String phaseName, Long categoryId, String categoryName,
        Long employeeId, String employeeName, String description, List<DocumentResponse> documents, java.math.BigDecimal approvedAmount) {
    public record DocumentResponse(Long id, String name, String url, String publicId) {}
}
