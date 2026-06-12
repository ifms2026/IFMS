package com.mkwang.backend.modules.organization.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.organization.dto.request.CreateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.request.UpdateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.response.DepartmentDetailResponse;
import com.mkwang.backend.modules.organization.dto.response.DepartmentSummaryResponse;
import com.mkwang.backend.modules.organization.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/departments")
@RequiredArgsConstructor
@Tag(name = "Admin — Departments", description = "Admin department management APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "List all departments with optional search and pagination")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentSummaryResponse>>> getDepartments(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        PageResponse<DepartmentSummaryResponse> result =
                departmentService.getDepartments(search, page, limit);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department detail including members")
    public ResponseEntity<ApiResponse<DepartmentDetailResponse>> getDepartmentDetail(
            @PathVariable Long id) {
        DepartmentDetailResponse result = departmentService.getDepartmentDetail(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "Create a new department")
    public ResponseEntity<ApiResponse<DepartmentDetailResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentDetailResponse result = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update department info, manager or budget quota")
    public ResponseEntity<ApiResponse<DepartmentDetailResponse>> updateDepartment(
            @PathVariable Long id,
            @RequestBody UpdateDepartmentRequest request) {
        DepartmentDetailResponse result = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
