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
    private final WalletService walletService;
    private final NotificationPublisher notificationPublisher;

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

        BigDecimal totalNetPayroll = period.getPayslips().stream()
                .map(Payslip::getFinalNetSalary)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return payrollManagementMapper.toDetailResponse(period, entries.size(), totalNetPayroll, entries);
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
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Payroll");

            // Header row
            String[] headers = {"employeeCode", "employeeName", "baseSalary", "bonus", "allowance", "deduction"};
            Row headerRow = sheet.createRow(0);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Sample row so users understand the format
            Row sampleRow = sheet.createRow(1);
            sampleRow.createCell(0).setCellValue("MK001");
            sampleRow.createCell(1).setCellValue("Nguyen Van A");
            sampleRow.createCell(2).setCellValue(28000000);
            sampleRow.createCell(3).setCellValue(2000000);
            sampleRow.createCell(4).setCellValue(0);
            sampleRow.createCell(5).setCellValue(2800000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new com.mkwang.backend.common.exception.InternalSystemException(
                    "Failed to generate payroll template: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/import
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollImportResultResponse importPayroll(Long periodId, MultipartFile file) {
        // 1. Validate file
        validateImportFile(file);

        // 2. Load period
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        // 3. Reject if period already has payslips
        if (!period.getPayslips().isEmpty()) {
            throw new ConflictException(
                    "Period already has " + period.getPayslips().size() + " payslip(s). " +
                    "Call POST /accountant/payroll/" + periodId + "/confirm-overwrite to replace them.");
        }

        // 4. Parse Excel rows
        List<PayrollImportEntryResponse> entries = new ArrayList<>();
        List<PayrollImportErrorResponse> errors = new ArrayList<>();
        List<Payslip> toSave = new ArrayList<>();
        BigDecimal totalNetPayroll = BigDecimal.ZERO;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = 0;

            // Row 0 = header, data starts at row 1
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                totalRows++;
                int humanRowNum = rowIndex + 1; // 1-based for error messages

                // Read cells
                String employeeCode = stringCell(row, 0);
                String employeeName  = stringCell(row, 1);
                BigDecimal baseSalary = decimalCell(row, 2);
                BigDecimal bonus      = decimalCell(row, 3);
                BigDecimal allowance  = decimalCell(row, 4);
                BigDecimal deduction  = decimalCell(row, 5);


                // Field-level validation
                List<PayrollImportErrorResponse> rowErrors = new ArrayList<>();

                if (employeeCode == null || employeeCode.isBlank()) {
                    rowErrors.add(new PayrollImportErrorResponse(humanRowNum, "employeeCode", "employeeCode is required"));
                }
                if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) <= 0) {
                    rowErrors.add(new PayrollImportErrorResponse(humanRowNum, "baseSalary", "baseSalary must be a positive number"));
                }


                if (!rowErrors.isEmpty()) {
                    errors.addAll(rowErrors);
                    entries.add(errorEntry(employeeName, employeeCode, baseSalary, bonus, allowance, deduction,
                            rowErrors.get(0).message()));
                    continue;
                }

                // Look up user by employeeCode
                Optional<UserProfile> profileOpt = userProfileRepository.findByEmployeeCode(employeeCode);
                if (profileOpt.isEmpty()) {
                    String msg = "Employee not found in system (row " + humanRowNum + ")";
                    errors.add(new PayrollImportErrorResponse(humanRowNum, "employeeCode", "Employee not found in system"));
                    entries.add(errorEntry(employeeName, employeeCode, baseSalary, bonus, allowance, deduction, msg));
                    continue;
                }

                UserProfile profile = profileOpt.get();
                User user = profile.getUser();

                // Generate payslip code: PSL-{EMP_CODE}-{MMYY}
                String payslipCode = String.format("PSL-%s-%02d%02d",
                        employeeCode, period.getMonth(), period.getYear() % 100);

                BigDecimal safeBonus     = bonus     != null ? bonus     : BigDecimal.ZERO;
                BigDecimal safeAllowance = allowance != null ? allowance : BigDecimal.ZERO;
                BigDecimal safeDeduction = deduction != null ? deduction : BigDecimal.ZERO;

                // finalNetSalary = baseSalary + bonus + allowance - deduction (advanceDeduct applied later by auto-netting)
                BigDecimal finalNet = baseSalary.add(safeBonus).add(safeAllowance).subtract(safeDeduction);

                if(finalNet.compareTo(BigDecimal.ZERO) < 0) {
                    String msg = "Final net salary cannot be negative (row " + humanRowNum + ")";
                    errors.add(new PayrollImportErrorResponse(humanRowNum, "deduction", "Deductions exceed total earnings"));
                    entries.add(errorEntry(employeeName, employeeCode, baseSalary, bonus, allowance, deduction, msg));
                    continue;

                }

                Payslip payslip = Payslip.builder()
                        .payslipCode(payslipCode)
                        .period(period)
                        .user(user)
                        .baseSalary(baseSalary)
                        .bonus(safeBonus)
                        .allowance(safeAllowance)
                        .deduction(safeDeduction)
                        .advanceDeduct(BigDecimal.ZERO)
                        .finalNetSalary(finalNet)
                        .status(PayslipStatus.DRAFT)
                        .build();

                toSave.add(payslip);
                totalNetPayroll = totalNetPayroll.add(finalNet);

                entries.add(new PayrollImportEntryResponse(
                        null,          // id — set after save below
                        payslipCode,
                        user.getId(),
                        user.getFullName(),
                        employeeCode,
                        baseSalary,
                        safeBonus,
                        safeAllowance,
                        safeDeduction,
                        BigDecimal.ZERO,
                        finalNet,
                        PayslipStatus.DRAFT.name(),
                        "ok",
                        null
                ));
            }

            // 5. Persist successful payslips
            List<Payslip> saved = payslipRepository.saveAll(toSave);

            // Back-fill ids into ok entries
            int savedIdx = 0;
            for (int i = 0; i < entries.size(); i++) {
                PayrollImportEntryResponse e = entries.get(i);
                if ("ok".equals(e.importStatus())) {
                    Payslip s = saved.get(savedIdx++);
                    entries.set(i, new PayrollImportEntryResponse(
                            s.getId(), e.payslipCode(), e.userId(), e.fullName(), e.employeeCode(),
                            e.baseSalary(), e.bonus(), e.allowance(), e.deduction(),
                            e.advanceDeduct(), e.finalNetSalary(), e.status(), "ok", null
                    ));
                }
            }

            int successCount = toSave.size();
            int errorCount   = errors.size();

            return new PayrollImportResultResponse(
                    period.getId(),
                    period.getPeriodCode(),
                    period.getStatus().name(),
                    successCount + errorCount,
                    successCount,
                    errorCount,
                    entries,
                    errors,
                    totalNetPayroll
            );

        } catch (IOException e) {
            throw new BadRequestException("Failed to read Excel file: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/confirm-overwrite
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public String confirmOverwrite(Long periodId) {
        if (!payrollPeriodRepository.existsById(periodId)) {
            throw new ResourceNotFoundException("PayrollPeriod", "id", periodId);
        }
        payslipRepository.deleteAllByPeriodId(periodId);
        return "Previous payroll data cleared. Ready for re-import.";
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /accountant/payroll/:periodId/auto-netting
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AutoNettingResponse autoNetting(Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        if (!period.isEditable()) {
            throw new BadRequestException("Auto-netting is only allowed for DRAFT periods");
        }

        List<AutoNettingSummaryEntryResponse> summary = new ArrayList<>();
        BigDecimal totalAdvanceDeducted = BigDecimal.ZERO;

        for (Payslip payslip : period.getPayslips()) {
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
        PayrollPeriod period = payrollPeriodRepository.findDetailById(periodId)
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

        BigDecimal totalNetPayroll = BigDecimal.ZERO;

        for (Payslip payslip : period.getPayslips()) {
            BigDecimal finalNet = safe(payslip.getFinalNetSalary());

            if (finalNet.compareTo(BigDecimal.ZERO) > 0) {
                walletService.transfer(
                        WalletOwnerType.COMPANY_FUND, 1L,
                        WalletOwnerType.USER, payslip.getUser().getId(),
                        finalNet,
                        TransactionType.PAYSLIP_PAYMENT,
                        ReferenceType.PAYSLIP, payslip.getId(),
                        "Payslip " + payslip.getPayslipCode()
                );
            }

            BigDecimal advanceDeduct = safe(payslip.getAdvanceDeduct());
            if (advanceDeduct.compareTo(BigDecimal.ZERO) > 0) {
                requestService.applyPayrollDeduction(payslip.getUser().getId(), advanceDeduct);
            }

            payslip.setStatus(PayslipStatus.PAID);
            payslip.setPaymentDate(LocalDateTime.now());
            totalNetPayroll = totalNetPayroll.add(finalNet);

            // Notify employee: lương đã được chi trả
            try {
                User employee = payslip.getUser();
                notificationPublisher.publish(new NotificationEvent(
                        employee.getId(),
                        employee.getEmail(),
                        "SALARY_PAID",
                        "Lương đã được thanh toán",
                        "Phiếu lương " + payslip.getPayslipCode()
                                + " đã được chi trả. Số tiền: "
                                + String.format("%,.0f", finalNet) + " ₫",
                        payslip.getId(),
                        "PAYSLIP"
                ));
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
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

        if (!period.isEditable()) {
            throw new BadRequestException("Payslip entries can only be edited when the period is DRAFT");
        }

        Payslip payslip = payslipRepository.findByIdAndPeriodId(payslipId, periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", "id", payslipId));

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
        payslip.setFinalNetSalary(finalNet);

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
}

