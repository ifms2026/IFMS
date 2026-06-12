package com.mkwang.backend.modules.organization.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ConflictException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.organization.dto.request.CreateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.request.UpdateDepartmentRequest;
import com.mkwang.backend.modules.organization.dto.response.DepartmentDetailResponse;
import com.mkwang.backend.modules.organization.dto.response.DepartmentSummaryResponse;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.organization.mapper.DepartmentMapper;
import com.mkwang.backend.modules.organization.repository.DepartmentRepository;
import com.mkwang.backend.modules.organization.repository.DepartmentSpecification;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserService          userService;
    private final DepartmentMapper     departmentMapper;

    // ── GET /admin/departments ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DEPT_MANAGE')")
    public PageResponse<DepartmentSummaryResponse> getDepartments(String search, int page, int limit) {
        Specification<Department> spec = DepartmentSpecification.filter(search);
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("name").ascending());
        Page<Department> result = departmentRepository.findAll(spec, pageable);

        List<DepartmentSummaryResponse> items = result.getContent().stream()
                .map(dept -> {
                    long count = userService.countUsersByDepartmentId(dept.getId());
                    return departmentMapper.toSummary(dept, count);
                })
                .toList();

        return PageResponse.<DepartmentSummaryResponse>builder()
                .items(items)
                .total(result.getTotalElements())
                .page(page)
                .size(limit)
                .totalPages(result.getTotalPages())
                .build();
    }

    // ── GET /admin/departments/:id ────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DEPT_MANAGE')")
    public DepartmentDetailResponse getDepartmentDetail(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));

        List<User> members = userService.getUsersByDepartmentIdWithProfile(id);
        return departmentMapper.toDetail(dept, members);
    }

    // ── POST /admin/departments ───────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DEPT_MANAGE')")
    public DepartmentDetailResponse createDepartment(CreateDepartmentRequest request) {
        String name = request.getName().trim();

        if (departmentRepository.existsByName(name)) {
            throw new ConflictException("Department name already exists: " + name);
        }

        String code = resolveCode(request.getCode(), name);
        if (departmentRepository.existsByCode(code)) {
            throw new ConflictException("Department code already exists: " + code);
        }

        User manager = null;
        if (request.getManagerId() != null) {
            manager = userService.getUserById(request.getManagerId());
        }

        BigDecimal quota = request.getTotalProjectQuota() != null
                ? request.getTotalProjectQuota()
                : BigDecimal.ZERO;

        Department dept = Department.builder()
                .name(name)
                .code(code)
                .manager(manager)
                .totalProjectQuota(quota)
                .totalAvailableBalance(quota)
                .build();

        dept = departmentRepository.save(dept);
        log.info("Admin created department: {} ({})", name, code);

        return departmentMapper.toDetail(dept, List.of());
    }

    // ── PUT /admin/departments/:id ────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DEPT_MANAGE')")
    public DepartmentDetailResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));

        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            if (departmentRepository.existsByNameAndIdNot(newName, id)) {
                throw new ConflictException("Department name already exists: " + newName);
            }
            dept.setName(newName);
        }

        if (request.getManagerId() != null) {
            User manager = userService.getUserById(request.getManagerId());
            dept.setManager(manager);
        }

        if (request.getTotalProjectQuota() != null) {
            BigDecimal newQuota = request.getTotalProjectQuota();
            if (newQuota.compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("totalProjectQuota cannot be negative");
            }
            BigDecimal diff = newQuota.subtract(dept.getTotalProjectQuota());
            dept.setTotalProjectQuota(newQuota);
            dept.setTotalAvailableBalance(dept.getTotalAvailableBalance().add(diff));
        }

        departmentRepository.save(dept);

        List<User> members = userService.getUsersByDepartmentIdWithProfile(id);
        return departmentMapper.toDetail(dept, members);
    }

    @Override
    @Transactional(readOnly = true)
    public long countDepartments() {
        return departmentRepository.count();
    }

    // ── Private helpers ──────────────────────────────────────────

    private String resolveCode(String requestedCode, String name) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            return requestedCode.trim().toUpperCase();
        }
        return generateCodeFromName(name);
    }

    private String generateCodeFromName(String name) {
        String[] words = name.trim().split("[\\s&,_-]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        String base = sb.toString();
        if (base.length() < 2) {
            base = name.toUpperCase().replaceAll("[^A-Z]", "");
            base = base.length() > 5 ? base.substring(0, 5) : base;
        }
        return base;
    }
}
