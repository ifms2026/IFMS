package com.mkwang.backend.modules.accounting.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.accounting.dto.request.CreatePayrollPeriodRequest;
import com.mkwang.backend.modules.accounting.dto.request.UpdatePayslipEntryRequest;
import com.mkwang.backend.modules.accounting.dto.response.AutoNettingResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollEntryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollImportResultResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollPeriodDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollPeriodSummaryResponse;
import com.mkwang.backend.modules.accounting.dto.response.PayrollRunResponse;
import com.mkwang.backend.modules.accounting.entity.PayrollStatus;
import com.mkwang.backend.modules.accounting.service.PayrollManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/accountant/payroll")
@RequiredArgsConstructor
@Tag(name = "Accountant - Payroll", description = "Payroll period management for accountants")
@SecurityRequirement(name = "bearerAuth")
public class AccountantPayrollController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PayrollManagementService payrollManagementService;

    @GetMapping
    @Operation(summary = "Get payroll periods",
               description = "Returns paginated payroll periods with aggregate employee count and total net payroll.")
    public ResponseEntity<ApiResponse<PageResponse<PayrollPeriodSummaryResponse>>> getPayrollPeriods(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) PayrollStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                payrollManagementService.getPayrollPeriods(year, status, page, limit)
        ));
    }

    @GetMapping("/{periodId}")
    @Operation(summary = "Get payroll period detail",
               description = "Returns payroll period detail with all payslip entries.")
    public ResponseEntity<ApiResponse<PayrollPeriodDetailResponse>> getPayrollPeriodDetail(
            @PathVariable Long periodId) {

        return ResponseEntity.ok(ApiResponse.success(
                payrollManagementService.getPayrollPeriodDetail(periodId)
        ));
    }

    @PostMapping
    @Operation(summary = "Create payroll period",
               description = "Creates a new payroll period and auto-generates periodCode.")
    public ResponseEntity<ApiResponse<PayrollPeriodDetailResponse>> createPayrollPeriod(
            @Valid @RequestBody CreatePayrollPeriodRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                payrollManagementService.createPayrollPeriod(request)
        ));
    }

    @GetMapping("/template")
    @Operation(summary = "Download payroll Excel template",
               description = "Returns a .xlsx template with columns: employeeCode, employeeName, baseSalary, bonus, allowance, deduction. " +
                             "advanceDeduct is intentionally excluded — it is auto-calculated by POST auto-netting.")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] bytes = payrollManagementService.downloadTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("payroll_template.xlsx").build());
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping(value = "/{periodId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import payroll from Excel",
               description = "Parses an uploaded .xlsx/.xls file, maps each row to a Payslip for the given period. " +
                             "Returns per-row import status. Rows with errors are not persisted. " +
                             "Returns 409 Conflict if the period already has payslips.")
    public ResponseEntity<ApiResponse<PayrollImportResultResponse>> importPayroll(
            @PathVariable Long periodId,
            @RequestPart("file") MultipartFile file) {

        PayrollImportResultResponse result = payrollManagementService.importPayroll(periodId, file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{periodId}/confirm-overwrite")
    @Operation(summary = "Confirm payroll overwrite",
               description = "Deletes all existing payslips for the period so a fresh import can proceed. " +
                             "Call this after receiving a 409 from the import endpoint.")
    public ResponseEntity<ApiResponse<String>> confirmOverwrite(@PathVariable Long periodId) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollManagementService.confirmOverwrite(periodId)));
    }

    @PostMapping("/{periodId}/auto-netting")
    @Operation(summary = "Auto-net advance deductions",
               description = "Computes advanceDeduct per payslip using each employee's outstanding AdvanceBalance debt. " +
                             "Cap rule: deductedAmount = min(outstandingDebt, 50% × grossBeforeAdvance). " +
                             "Updates payslip.advanceDeduct and payslip.finalNetSalary. " +
                             "Must be called before /run. Safe to call multiple times on DRAFT periods.")
    public ResponseEntity<ApiResponse<AutoNettingResponse>> autoNetting(@PathVariable Long periodId) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollManagementService.autoNetting(periodId)));
    }

    @PostMapping("/{periodId}/run")
    @Operation(summary = "Run payroll",
               description = "Executes payroll: transfers finalNetSalary from CompanyFund to each employee's wallet " +
                             "via PAYSLIP_PAYMENT transactions, settles AdvanceBalance records for advance deductions, " +
                             "marks payslips PAID and period COMPLETED. " +
                             "Returns 422 if auto-netting has not been applied.")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> runPayroll(@PathVariable Long periodId) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollManagementService.runPayroll(periodId)));
    }

    @PutMapping("/{periodId}/entries/{payslipId}")
    @Operation(summary = "Update a payslip entry",
               description = "Partially updates a single payslip. All fields optional. " +
                             "Backend auto-recalculates finalNetSalary. " +
                             "Only allowed when period status is DRAFT.")
    public ResponseEntity<ApiResponse<PayrollEntryResponse>> updatePayslipEntry(
            @PathVariable Long periodId,
            @PathVariable Long payslipId,
            @RequestBody UpdatePayslipEntryRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                payrollManagementService.updatePayslipEntry(periodId, payslipId, request)));
    }
}

