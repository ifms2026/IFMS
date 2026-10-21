package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ConflictException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.common.exception.UnprocessableEntityException;
import com.mkwang.backend.modules.accounting.dto.request.CreatePayrollPeriodRequest;
import com.mkwang.backend.modules.accounting.dto.request.UpdatePayslipEntryRequest;
import com.mkwang.backend.modules.accounting.dto.response.AutoNettingResponse;
import com.mkwang.backend.modules.accounting.dto.response.AutoNettingSummaryEntryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollEntryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollImportEntryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollImportErrorResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollImportResultResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollPeriodDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollPeriodSummaryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollRunResponse;
import com.mkwang.backend.modules.accounting.entity.PayrollPeriod;
import com.mkwang.backend.modules.accounting.entity.PayrollStatus;
import com.mkwang.backend.modules.accounting.entity.Payslip;
import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import com.mkwang.backend.modules.accounting.mapper.PayrollManagementMapper;
import com.mkwang.backend.modules.accounting.repository.PayrollPeriodRepository;
import com.mkwang.backend.modules.accounting.repository.PayrollPeriodSpecification;
import com.mkwang.backend.modules.accounting.repository.PayslipRepository;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.profile.repository.UserProfileRepository;
import com.mkwang.backend.modules.notification.publisher.NotificationEvent;
import com.mkwang.backend.modules.notification.publisher.NotificationPublisher;
import com.mkwang.backend.modules.request.service.RequestService;
import com.mkwang.backend.modules.accounting.service.AccountingJournalService;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.service.WalletService;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PayrollManagementServiceImpl implements PayrollManagementService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollManagementMapper payrollManagementMapper;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final UserProfileRepository userProfileRepository;
    private final RequestService requestService;
    private final AccountingJournalService accountingJournalService;
    private final WalletService walletService;
    private final NotificationPublisher notificationPublisher;
    private final PayrollExcelDataset payrollExcelDataset;
    private final com.mkwang.backend.modules.spreadsheet.service.ExcelWorkbookService excelWorkbooks;

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/payroll
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<PayrollPeriodSummaryResponse> getPayrollPeriods(Integer year, PayrollStatus status, int page, int limit) {
        if (page < 1) {
            throw new BadRequestException("page must be >= 1");
        }
        if (limit <= 0) {
            throw new BadRequestException("limit must be > 0");
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(
                Sort.Order.desc("year"),
                Sort.Order.desc("month"),
                Sort.Order.desc("createdAt")));

        Specification<PayrollPeriod> specification = PayrollPeriodSpecification.filter(year, status);
        Page<PayrollPeriod> periodPage = payrollPeriodRepository.findAll(specification, pageable);

        List<Long> periodIds = periodPage.getContent().stream().map(PayrollPeriod::getId).toList();
        Map<Long, AggregateMetrics> aggregateMetricsByPeriodId = aggregateByPeriodIds(periodIds);

        List<PayrollPeriodSummaryResponse> items = periodPage.getContent().stream()
                .map(period -> {
                    AggregateMetrics metrics = aggregateMetricsByPeriodId.getOrDefault(period.getId(), AggregateMetrics.empty());
                    return payrollManagementMapper.toSummaryResponse(period, metrics.employeeCount(), metrics.totalNetPayroll());
                })
                .toList();

        return PageResponse.<PayrollPeriodSummaryResponse>builder()
                .items(items)
                .total(periodPage.getTotalElements())
                .page(page)
                .size(limit)
                .totalPages(periodPage.getTotalPages())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/payroll/:periodId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollPeriodDetailResponse getPayrollPeriodDetail(Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        List<PayrollEntryResponse> entries = period.getPayslips().stream()
                .map(payrollManagementMapper::toEntryResponse)
                .toList();

        BigDecimal totalNetPayroll = period.getPayslips().stream().filter(p -> p.getStatus() != PayslipStatus.REVERSED)
                .map(Payslip::getFinalNetSalary)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return payrollManagementMapper.toDetailResponse(period, (int)period.getPayslips().stream().filter(p -> p.getStatus() != PayslipStatus.REVERSED).count(), totalNetPayroll, entries);
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollPeriodDetailResponse createPayrollPeriod(CreatePayrollPeriodRequest request) {
        validateDateRange(request);

        if (payrollPeriodRepository.existsByMonthAndYear(request.month(), request.year())) {
            throw new ConflictException("Payroll period already exists for month=" + request.month() + " and year=" + request.year());
        }

        String periodCode = businessCodeGenerator.generate(
                BusinessCodeType.PERIOD,
                String.valueOf(request.year()),
                String.valueOf(request.month())
        );

        PayrollPeriod period = PayrollPeriod.builder()
                .periodCode(periodCode)
                .name(request.name())
                .month(request.month())
                .year(request.year())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(PayrollStatus.DRAFT)
                .build();

        PayrollPeriod saved = payrollPeriodRepository.save(period);
        return payrollManagementMapper.toDetailResponse(saved, 0, BigDecimal.ZERO, List.of());
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/payroll/template
    // ─────────────────────────────────────────────────────────────────

    @Override
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public byte[] downloadTemplate() {
        return excelWorkbooks.template(payrollExcelDataset, payrollExcelDataset.references(null, null));
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/import
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollImportResultResponse importPayroll(Long periodId, MultipartFile file) {
        throw new BadRequestException("Sử dụng Nhập/xuất Excel: xem trước /spreadsheets/payroll/preview rồi xác nhận /spreadsheets/batches/{id}/commit");
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/confirm-overwrite
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public String confirmOverwrite(Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));
        assertDraft(period);
        if (period.getPayslips().stream().anyMatch(p -> p.getStatus() == PayslipStatus.PAID)) {
            throw new BadRequestException("Cannot remove payslips that have been paid");
        }
        payslipRepository.deleteAllByPeriodId(periodId);
        period.setNettingApplied(false);
        return "Previous payroll data cleared. Ready for re-import.";
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/auto-netting
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AutoNettingResponse autoNetting(Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        if (!period.isEditable()) {
            throw new BadRequestException("Auto-netting is only allowed for DRAFT periods");
        }

        List<AutoNettingSummaryEntryResponse> summary = new ArrayList<>();
        BigDecimal totalAdvanceDeducted = BigDecimal.ZERO;

        for (Payslip payslip : period.getPayslips().stream()
                .sorted(java.util.Comparator.comparing(p -> p.getUser().getId())).toList()) {
            Long userId = payslip.getUser().getId();
            BigDecimal outstandingDebt = requestService.getTotalOutstandingDebt(userId);

            BigDecimal grossBeforeAdvance = safe(payslip.getBaseSalary())
                    .add(safe(payslip.getBonus()))
                    .add(safe(payslip.getAllowance()))
                    .subtract(safe(payslip.getDeduction()));

            BigDecimal cap = grossBeforeAdvance
                    .multiply(BigDecimal.valueOf(0.5))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal deductedAmount = outstandingDebt.min(cap);
            payslip.setAdvanceDeduct(deductedAmount);
            payslip.setFinalNetSalary(grossBeforeAdvance.subtract(deductedAmount));

            if (outstandingDebt.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingDebt = outstandingDebt.subtract(deductedAmount);
                String note = deductedAmount.compareTo(outstandingDebt) >= 0
                        ? "Full clearance"
                        : "Partial deduction — capped at 50% net salary";

                UserProfile profile = payslip.getUser().getProfile();
                summary.add(new AutoNettingSummaryEntryResponse(
                        userId,
                        profile != null ? profile.getEmployeeCode() : null,
                        payslip.getUser().getFullName(),
                        outstandingDebt,
                        deductedAmount,
                        remainingDebt,
                        note
                ));
                totalAdvanceDeducted = totalAdvanceDeducted.add(deductedAmount);
            }
        }

        payslipRepository.saveAll(period.getPayslips());
        period.setNettingApplied(true);

        return new AutoNettingResponse(
                period.getId(),
                period.getPeriodCode(),
                totalAdvanceDeducted,
                summary
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/run
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollRunResponse runPayroll(Long periodId) {
        payrollPeriodRepository.lockPayrollRun();
        PayrollPeriod period = payrollPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        if (!period.isEditable()) {
            throw new BadRequestException("Only DRAFT periods can be run");
        }
        if (period.getPayslips().isEmpty()) {
            throw new BadRequestException("Period has no payslips. Import payroll data first.");
        }
        if (!period.isNettingApplied()) {
            throw new UnprocessableEntityException(
                    "Auto-netting must be applied before running payroll. " +
                    "Call POST /accountant/payroll/" + periodId + "/auto-netting first.");
        }

        period.getPayslips().stream().map(p -> p.getUser().getId()).distinct().sorted().forEach(requestService::lockEmployeeFinancialScope);
        BigDecimal totalNetPayroll = BigDecimal.ZERO;

        for (Payslip payslip : period.getPayslips().stream()
                .sorted(java.util.Comparator.comparing(p -> p.getUser().getId())).toList()) {
            if (payslip.getStatus() != PayslipStatus.DRAFT) throw new BadRequestException("Period contains a non-draft payslip");
            BigDecimal earnings = safe(payslip.getBaseSalary()).add(safe(payslip.getBonus()))
                    .add(safe(payslip.getAllowance())).subtract(safe(payslip.getDeduction()));
            BigDecimal offset = safe(payslip.getAdvanceDeduct());
            BigDecimal expectedOffset = requestService.getTotalOutstandingDebt(payslip.getUser().getId())
                    .min(earnings.multiply(new BigDecimal("0.5")).setScale(2,RoundingMode.HALF_UP));
            if (offset.compareTo(expectedOffset) != 0 || earnings.signum() < 0 || offset.signum() < 0 || offset.compareTo(earnings.multiply(new BigDecimal("0.5")).setScale(2,RoundingMode.HALF_UP)) > 0
                    || offset.compareTo(requestService.getTotalOutstandingDebt(payslip.getUser().getId())) > 0
                    || earnings.subtract(offset).compareTo(safe(payslip.getFinalNetSalary())) != 0) {
                throw new BadRequestException("Payroll figures or advance balances changed; apply auto-netting again");
            }
            BigDecimal finalNet = safe(payslip.getFinalNetSalary());
            Transaction payrollTransaction = null;

            BigDecimal advanceDeduct = safe(payslip.getAdvanceDeduct());
            List<com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse> advanceAllocations = List.of();
            if (advanceDeduct.compareTo(BigDecimal.ZERO) > 0) {
                advanceAllocations = requestService.applyPayrollDeduction(payslip.getUser().getId(), advanceDeduct);
            }

            if (finalNet.compareTo(BigDecimal.ZERO) > 0) {
                payrollTransaction = walletService.transfer(
                        WalletOwnerType.COMPANY_FUND, 1L,
                        WalletOwnerType.USER, payslip.getUser().getId(),
                        finalNet,
                        TransactionType.PAYSLIP_PAYMENT,
                        ReferenceType.PAYSLIP, payslip.getId(),
                        "Payslip " + payslip.getPayslipCode()
                );
            }

            accountingJournalService.recordPayrollSettlement(payslip, payrollTransaction, advanceAllocations);

            payslip.setStatus(PayslipStatus.PAID);
            payslip.setPaymentDate(LocalDateTime.now());
            totalNetPayroll = totalNetPayroll.add(finalNet);

            // Notify employee: lương đã được chi trả
            try {
                User employee = payslip.getUser();
                com.mkwang.backend.common.utils.AfterCommit.run(() -> notificationPublisher.publish(new NotificationEvent(
                        employee.getId(),
                        employee.getEmail(),
                        "SALARY_PAID",
                        "Lương đã được thanh toán",
                        "Phiếu lương " + payslip.getPayslipCode()
                                + " đã được chi trả. Số tiền: "
                                + String.format("%,.0f", finalNet) + " ₫",
                        payslip.getId(),
                        "PAYSLIP"
                )));
            } catch (Exception e) {
                // Notification failure must never break the payroll transaction
            }
        }

        period.setStatus(PayrollStatus.COMPLETED);

        return new PayrollRunResponse(
                period.getId(),
                period.getPeriodCode(),
                PayrollStatus.COMPLETED,
                period.getPayslips().size(),
                totalNetPayroll
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // PUT /accountant/payroll/:periodId/entries/:payslipId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollEntryResponse updatePayslipEntry(Long periodId, Long payslipId, UpdatePayslipEntryRequest request) {
        PayrollPeriod period = payrollPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        if (!period.isEditable()) {
            throw new BadRequestException("Payslip entries can only be edited when the period is DRAFT");
        }

        Payslip payslip = payslipRepository.findByIdAndPeriodId(payslipId, periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", "id", payslipId));

        for (BigDecimal value : new BigDecimal[]{request.baseSalary(),request.bonus(),request.allowance(),request.deduction(),request.advanceDeduct()}) {
            if (value != null && (value.signum() < 0 || value.scale() > 2)) throw new BadRequestException("Các khoản lương phải không âm, tối đa 2 số lẻ");
        }
        if (request.baseSalary()    != null) payslip.setBaseSalary(request.baseSalary());
        if (request.bonus()         != null) payslip.setBonus(request.bonus());
        if (request.allowance()     != null) payslip.setAllowance(request.allowance());
        if (request.deduction()     != null) payslip.setDeduction(request.deduction());
        if (request.advanceDeduct() != null) payslip.setAdvanceDeduct(request.advanceDeduct());

        BigDecimal finalNet = safe(payslip.getBaseSalary())
                .add(safe(payslip.getBonus()))
                .add(safe(payslip.getAllowance()))
                .subtract(safe(payslip.getDeduction()))
                .subtract(safe(payslip.getAdvanceDeduct()));
        if (finalNet.signum() < 0) throw new BadRequestException("Final net salary cannot be negative");
        if (payslip.getStatus() != PayslipStatus.DRAFT) throw new BadRequestException("Paid payslips cannot be edited");
        payslip.setFinalNetSalary(finalNet);
        period.setNettingApplied(false);

        return payrollManagementMapper.toEntryResponse(payslip);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<com.mkwang.backend.modules.accounting.entity.PayrollPeriod> getLatestPayrollPeriod() {
        return payrollPeriodRepository.findLatestPeriod();
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private void assertDraft(PayrollPeriod period) {
        if (!period.isEditable()) throw new BadRequestException("Only DRAFT payroll periods can be changed");
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Import file is required");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls"))) {
            throw new BadRequestException("Only .xlsx or .xls files are supported");
        }
        long maxBytes = 10L * 1024 * 1024; // 10 MB
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File size must not exceed 10 MB");
        }
    }

    private String stringCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return cell.getStringCellValue().trim();
    }

    private BigDecimal decimalCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        try {
            return new BigDecimal(cell.getStringCellValue().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private PayrollImportEntryResponse errorEntry(
            String employeeName, String employeeCode,
            BigDecimal baseSalary, BigDecimal bonus, BigDecimal allowance, BigDecimal deduction,
            String errorMessage) {
        return new PayrollImportEntryResponse(
                null, null, null,
                employeeName, employeeCode,
                baseSalary, bonus, allowance, deduction,
                BigDecimal.ZERO, safe(baseSalary),
                null, "error", errorMessage
        );
    }

    private void validateDateRange(CreatePayrollPeriodRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
    }

    private Map<Long, AggregateMetrics> aggregateByPeriodIds(List<Long> periodIds) {
        if (periodIds.isEmpty()) return Map.of();
        Map<Long, AggregateMetrics> result = new HashMap<>();
        for (Object[] row : payslipRepository.aggregateByPeriodIds(periodIds)) {
            Long pid = ((Number) row[0]).longValue();
            int empCount = ((Number) row[1]).intValue();
            BigDecimal total = row[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            result.put(pid, new AggregateMetrics(empCount, total));
        }
        return result;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record AggregateMetrics(int employeeCount, BigDecimal totalNetPayroll) {
        private static AggregateMetrics empty() {
            return new AggregateMetrics(0, BigDecimal.ZERO);
        }
    }

    private record PayrollTemplateRow(
            String employeeCode,
            String employeeName,
            double baseSalary,
            double bonus,
            double allowance,
            double deduction
    ) {}
}

