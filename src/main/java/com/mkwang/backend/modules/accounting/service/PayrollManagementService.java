package com.mkwang.backend.modules.accounting.service;

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
import org.springframework.web.multipart.MultipartFile;

public interface PayrollManagementService {

    PageResponse<PayrollPeriodSummaryResponse> getPayrollPeriods(Integer year, PayrollStatus status, int page, int limit);

    PayrollPeriodDetailResponse getPayrollPeriodDetail(Long periodId);

    PayrollPeriodDetailResponse createPayrollPeriod(CreatePayrollPeriodRequest request);

    /**
     * Generate and return the Excel template file bytes for payroll import.
     * Columns: employeeCode, employeeName, baseSalary, bonus, allowance, deduction.
     * advanceDeduct is intentionally excluded — it is auto-calculated by POST auto-netting.
     */
    byte[] downloadTemplate();

    /**
     * Parse an uploaded Excel file, map each row to a Payslip for the given period,
     * persist successful rows and return a detailed import result.
     *
     * @throws com.mkwang.backend.common.exception.ResourceNotFoundException if period not found
     * @throws com.mkwang.backend.common.exception.BadRequestException       if file type/size invalid
     * @throws com.mkwang.backend.common.exception.ConflictException         if period already has payslips
     */
    PayrollImportResultResponse importPayroll(Long periodId, MultipartFile file);

    /**
     * Delete all payslips for the given period to allow re-import.
     * Call this after receiving a 409 from importPayroll.
     *
     * @throws com.mkwang.backend.common.exception.ResourceNotFoundException if period not found
     */
    String confirmOverwrite(Long periodId);

    /**
     * Execute payroll: transfer finalNetSalary from CompanyFund to each employee's wallet,
     * settle AdvanceBalance records for the deducted amounts, mark payslips PAID and period COMPLETED.
     *
     * @throws com.mkwang.backend.common.exception.ResourceNotFoundException      if period not found
     * @throws com.mkwang.backend.common.exception.BadRequestException            if period is not DRAFT or has no payslips
     * @throws com.mkwang.backend.common.exception.UnprocessableEntityException   if auto-netting has not been applied
     */
    PayrollRunResponse runPayroll(Long periodId);

    /**
     * Partially update a single payslip entry. All fields optional — only sent fields are applied.
     * Recalculates finalNetSalary automatically. Period must be DRAFT.
     *
     * @throws com.mkwang.backend.common.exception.ResourceNotFoundException if period or payslip not found
     * @throws com.mkwang.backend.common.exception.BadRequestException       if period is not DRAFT
     */
    PayrollEntryResponse updatePayslipEntry(Long periodId, Long payslipId, UpdatePayslipEntryRequest request);

    /**
     * Compute advanceDeduct for each DRAFT payslip in the period using the employee's
     * outstanding AdvanceBalance debt. Updates payslip.advanceDeduct and payslip.finalNetSalary.
     * Cap rule: deductedAmount = min(outstandingDebt, 50% × grossBeforeAdvance).
     * Safe to call multiple times while status = DRAFT.
     *
     * @throws com.mkwang.backend.common.exception.ResourceNotFoundException if period not found
     * @throws com.mkwang.backend.common.exception.BadRequestException       if period is not DRAFT
     */
    AutoNettingResponse autoNetting(Long periodId);

    /**
     * Return the latest payroll period, or empty if none exists.
     * Used by Accountant dashboard.
     */
    java.util.Optional<com.mkwang.backend.modules.accounting.entity.PayrollPeriod> getLatestPayrollPeriod();
}

