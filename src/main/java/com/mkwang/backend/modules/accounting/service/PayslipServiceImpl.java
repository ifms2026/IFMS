package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.accounting.dto.response.PayslipDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipEmployeeResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayslipListItemResponse;
import com.mkwang.backend.modules.accounting.entity.Payslip;
import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import com.mkwang.backend.modules.accounting.repository.PayslipRepository;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PayslipServiceImpl implements PayslipService {

    private final PayslipRepository payslipRepository;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_VIEW_SELF')")
    public PageResponse<PayslipListItemResponse> getMyPayslips(
            Long userId,
            Integer year,
            PayslipStatus status,
            int page,
            int limit
    ) {
        if (page < 1) {
            throw new BadRequestException("page must be >= 1");
        }
        if (limit <= 0) {
            throw new BadRequestException("limit must be > 0");
        }

        Page<PayslipListItemResponse> payslipPage = payslipRepository
                .findMyPayslips(userId, year, status, PageRequest.of(page - 1, limit))
                .map(this::toListItem);

        return PageResponse.<PayslipListItemResponse>builder()
                .items(payslipPage.getContent())
                .total(payslipPage.getTotalElements())
                .page(payslipPage.getNumber() + 1)
                .size(payslipPage.getSize())
                .totalPages(payslipPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_VIEW_SELF')")
    public PayslipDetailResponse getMyPayslipById(Long userId, Long payslipId) {
        Payslip payslip = payslipRepository.findMyPayslipDetailById(userId, payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", "id", payslipId));
        return toDetailResponse(payslip);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayslipDetailResponse getPayslipById(Long payslipId) {
        Payslip payslip = payslipRepository.findPayslipDetailById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", "id", payslipId));

        return toDetailResponse(payslip);
    }

    private PayslipDetailResponse toDetailResponse(Payslip payslip) {
        BigDecimal totalEarnings = safe(payslip.getBaseSalary())
                .add(safe(payslip.getBonus()))
                .add(safe(payslip.getAllowance()));

        BigDecimal totalDeduction = safe(payslip.getDeduction())
                .add(safe(payslip.getAdvanceDeduct()));

        UserProfile profile = payslip.getUser().getProfile();
        Department department = payslip.getUser().getDepartment();

        PayslipEmployeeResponse employee = PayslipEmployeeResponse.builder()
                .id(payslip.getUser().getId())
                .fullName(payslip.getUser().getFullName())
                .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                .departmentName(department != null ? department.getName() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .bankName(profile != null ? profile.getBankName() : null)
                .bankAccountNum(maskBankAccount(profile != null ? profile.getBankAccountNum() : null))
                .build();

        return PayslipDetailResponse.builder()
                .id(payslip.getId())
                .payslipCode(payslip.getPayslipCode())
                .periodId(payslip.getPeriod().getId())
                .periodCode(payslip.getPeriod().getPeriodCode())
                .periodName(payslip.getPeriod().getName())
                .month(payslip.getPeriod().getMonth())
                .year(payslip.getPeriod().getYear())
                .status(payslip.getStatus())
                .baseSalary(safe(payslip.getBaseSalary()))
                .bonus(safe(payslip.getBonus()))
                .allowance(safe(payslip.getAllowance()))
                .totalEarnings(totalEarnings)
                .deduction(safe(payslip.getDeduction()))
                .advanceDeduct(safe(payslip.getAdvanceDeduct()))
                .totalDeduction(totalDeduction)
                .finalNetSalary(safe(payslip.getFinalNetSalary()))
                .employee(employee)
                .createdAt(payslip.getCreatedAt())
                .updatedAt(payslip.getUpdatedAt())
                .build();
    }

    private PayslipListItemResponse toListItem(Payslip payslip) {
        return PayslipListItemResponse.builder()
                .id(payslip.getId())
                .payslipCode(payslip.getPayslipCode())
                .periodId(payslip.getPeriod().getId())
                .periodName(payslip.getPeriod().getName())
                .month(payslip.getPeriod().getMonth())
                .year(payslip.getPeriod().getYear())
                .status(payslip.getStatus())
                .finalNetSalary(safe(payslip.getFinalNetSalary()))
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String maskBankAccount(String bankAccountNum) {
        if (bankAccountNum == null || bankAccountNum.isBlank()) {
            return null;
        }

        String normalized = bankAccountNum.trim();
        if (normalized.length() <= 4) {
            return "****" + normalized;
        }
        return "****" + normalized.substring(normalized.length() - 4);
    }
}

