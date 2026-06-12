package com.mkwang.backend.modules.accounting.mapper;

import com.mkwang.backend.modules.accounting.dto.response.PayrollEntryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollPeriodDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollPeriodSummaryResponse;
import com.mkwang.backend.modules.accounting.entity.PayrollPeriod;
import com.mkwang.backend.modules.accounting.entity.Payslip;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PayrollManagementMapper {

    public PayrollPeriodSummaryResponse toSummaryResponse(
            PayrollPeriod period,
            int employeeCount,
            BigDecimal totalNetPayroll) {

        return new PayrollPeriodSummaryResponse(
                period.getId(),
                period.getPeriodCode(),
                period.getName(),
                period.getMonth(),
                period.getYear(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus(),
                employeeCount,
                safe(totalNetPayroll),
                period.getCreatedAt(),
                period.getUpdatedAt()
        );
    }

    public PayrollPeriodDetailResponse toDetailResponse(
            PayrollPeriod period,
            int employeeCount,
            BigDecimal totalNetPayroll,
            List<PayrollEntryResponse> entries) {

        return new PayrollPeriodDetailResponse(
                period.getId(),
                period.getPeriodCode(),
                period.getName(),
                period.getMonth(),
                period.getYear(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus(),
                employeeCount,
                safe(totalNetPayroll),
                entries,
                period.getCreatedAt(),
                period.getUpdatedAt()
        );
    }

    public PayrollEntryResponse toEntryResponse(Payslip payslip) {
        UserProfile profile = payslip.getUser() != null ? payslip.getUser().getProfile() : null;
        return new PayrollEntryResponse(
                payslip.getId(),
                payslip.getPayslipCode(),
                payslip.getUser() != null ? payslip.getUser().getId() : null,
                payslip.getUser() != null ? payslip.getUser().getFullName() : null,
                profile != null && profile.getAvatarFile() != null ? profile.getAvatarFile().getUrl() : null,
                profile != null ? profile.getEmployeeCode() : null,
                profile != null ? profile.getJobTitle() : null,
                safe(payslip.getBaseSalary()),
                safe(payslip.getBonus()),
                safe(payslip.getAllowance()),
                safe(payslip.getDeduction()),
                safe(payslip.getAdvanceDeduct()),
                safe(payslip.getFinalNetSalary()),
                payslip.getStatus()
        );
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

