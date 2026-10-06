package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.accounting.dto.response.*;
import com.mkwang.backend.modules.accounting.entity.AccountingJournal;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalLine;
import com.mkwang.backend.modules.accounting.repository.AccountingJournalRepository;
import com.mkwang.backend.modules.accounting.repository.AccountingJournalSpecification;
import com.mkwang.backend.modules.accounting.entity.Payslip;
import com.mkwang.backend.modules.request.entity.AdvanceBalance;
import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.request.repository.AdvanceBalanceRepository;
import com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountingJournalServiceImpl implements AccountingJournalService {
    private static final String ACCOUNT_ADVANCE = "ADVANCE_RECEIVABLE";
    private static final String ACCOUNT_PROJECT_WALLET = "PROJECT_WALLET";
    private static final String ACCOUNT_EXPENSE = "PROJECT_EXPENSE";
    private static final String ACCOUNT_EMPLOYEE_PAYABLE = "EMPLOYEE_REIMBURSEMENT_PAYABLE";
    private static final String ACCOUNT_PAYROLL_EXPENSE = "PAYROLL_EXPENSE";
    private static final String ACCOUNT_PAYROLL_DEDUCTION = "PAYROLL_DEDUCTION_CLEARING";
    private static final String ACCOUNT_COMPANY_FUND = "COMPANY_FUND_WALLET";

    private final AccountingJournalRepository journalRepository;
    private final AdvanceBalanceRepository advanceBalanceRepository;

    @Override
    @Transactional
    public void recordAdvanceDisbursed(Request request, AdvanceBalance balance, Transaction transaction) {
        Long employeeId = request.getRequester().getId();
        Long projectId = request.getProject().getId();
        saveJournal(AccountingJournalEvent.ADVANCE_DISBURSED, "REQUEST", request.getId(), request.getId(),
                balance.getId(), employeeId, request.getRequester().getFullName(), projectId,
                request.getProject().getName(), transaction.getId(), transaction.getAmount(),
                "Giải ngân tạm ứng " + request.getRequestCode(), List.of(
                        debit(ACCOUNT_ADVANCE, "Tạm ứng nhân viên chưa quyết toán", transaction.getAmount(),
                                "Khoản nhân viên còn phải quyết toán tăng", balance.getId(), request.getId(), employeeId, projectId),
                        credit(ACCOUNT_PROJECT_WALLET, "Tiền trong ví dự án", transaction.getAmount(),
                                "Tiền trong ví dự án giảm khi giải ngân", balance.getId(), request.getId(), employeeId, projectId)));
    }

    @Override
    @Transactional
    public void recordExpenseVerified(Request request) {
        BigDecimal amount = request.getApprovedAmount();
        Long employeeId = request.getRequester().getId();
        Long projectId = request.getProject().getId();
        saveJournal(AccountingJournalEvent.EXPENSE_VERIFIED, "REQUEST", request.getId(), request.getId(), null,
                employeeId, request.getRequester().getFullName(), projectId, request.getProject().getName(), null, amount,
                "Chứng từ chi phí được xác nhận " + request.getRequestCode(), List.of(
                        debit(ACCOUNT_EXPENSE, "Chi phí dự án", amount, "Chi phí hợp lệ được ghi nhận", null,
                                request.getId(), employeeId, projectId),
                        credit(ACCOUNT_EMPLOYEE_PAYABLE, "Khoản phải hoàn cho nhân viên", amount,
                                "Khoản công ty phải hoàn cho nhân viên tăng", null, request.getId(), employeeId, projectId)));
    }

    @Override
    @Transactional
    public void recordExpensePaid(Request request, Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        Long employeeId = request.getRequester().getId();
        Long projectId = request.getProject().getId();
        saveJournal(AccountingJournalEvent.EXPENSE_PAID, "REQUEST", request.getId(), request.getId(), null,
                employeeId, request.getRequester().getFullName(), projectId, request.getProject().getName(),
                transaction.getId(), amount, "Thanh toán hoàn chi " + request.getRequestCode(), List.of(
                        debit(ACCOUNT_EMPLOYEE_PAYABLE, "Khoản phải hoàn cho nhân viên", amount,
                                "Khoản phải hoàn giảm sau khi thanh toán", null, request.getId(), employeeId, projectId),
                        credit(ACCOUNT_PROJECT_WALLET, "Tiền trong ví dự án", amount,
                                "Tiền trong ví dự án giảm khi hoàn chi", null, request.getId(), employeeId, projectId)));
    }

    @Override
    @Transactional
    public void recordReimburseSettled(Request request, AdvanceBalance balance) {
        BigDecimal amount = request.getApprovedAmount();
        Long employeeId = request.getRequester().getId();
        Long projectId = request.getProject().getId();
        saveJournal(AccountingJournalEvent.REIMBURSE_SETTLED, "REQUEST", request.getId(), request.getId(),
                balance.getId(), employeeId, request.getRequester().getFullName(), projectId,
                request.getProject().getName(), null, amount, "Quyết toán tạm ứng " + request.getRequestCode(), List.of(
                        debit(ACCOUNT_EXPENSE, "Chi phí dự án", amount, "Chi phí theo chứng từ hợp lệ tăng",
                                balance.getId(), request.getId(), employeeId, projectId),
                        credit(ACCOUNT_ADVANCE, "Tạm ứng nhân viên chưa quyết toán", amount,
                                "Khoản tạm ứng còn phải quyết toán giảm", balance.getId(), request.getId(), employeeId, projectId)));
    }

    @Override
    @Transactional
    public void recordAdvanceReturned(AdvanceBalance balance, Transaction transaction, BigDecimal amount, String description) {
        Request advanceRequest = balance.getAdvanceRequest();
        Long employeeId = balance.getUser().getId();
        Long projectId = advanceRequest.getProject().getId();
        saveJournal(AccountingJournalEvent.ADVANCE_RETURNED, "TRANSACTION", transaction.getId(), null,
                balance.getId(), employeeId, balance.getUser().getFullName(), projectId,
                advanceRequest.getProject().getName(), transaction.getId(), amount,
                description == null || description.isBlank() ? "Nhân viên hoàn tiền tạm ứng" : description,
                List.of(
                        debit(ACCOUNT_PROJECT_WALLET, "Tiền trong ví dự án", amount,
                                "Tiền hoàn được chuyển về ví dự án", balance.getId(), null, employeeId, projectId),
                        credit(ACCOUNT_ADVANCE, "Tạm ứng nhân viên chưa quyết toán", amount,
                                "Khoản tạm ứng còn phải quyết toán giảm", balance.getId(), null, employeeId, projectId)));
    }

    @Override
    @Transactional
    public void recordPayrollSettlement(Payslip payslip, Transaction transaction, List<AdvanceSettlementAllocationResponse> allocations) {
        BigDecimal base = nz(payslip.getBaseSalary());
        BigDecimal bonus = nz(payslip.getBonus());
        BigDecimal allowance = nz(payslip.getAllowance());
        BigDecimal deduction = nz(payslip.getDeduction());
        BigDecimal advanceOffset = nz(payslip.getAdvanceDeduct());
        BigDecimal net = nz(payslip.getFinalNetSalary());
        BigDecimal salaryGross = base.add(bonus).add(allowance);
        if (salaryGross.subtract(deduction).subtract(advanceOffset).compareTo(net) != 0) {
            throw new BadRequestException("Payslip " + payslip.getPayslipCode() + " does not reconcile: base + bonus + allowance must equal deduction + advance deduction + net salary");
        }
        BigDecimal allocated = allocations.stream().map(AdvanceSettlementAllocationResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(advanceOffset) != 0) {
            throw new BadRequestException("Payroll advance offsets do not match payslip advanceDeduct");
        }

        Long employeeId = payslip.getUser().getId();
        List<LineDraft> lines = new ArrayList<>();
        if (salaryGross.signum() > 0) {
            lines.add(debit(ACCOUNT_PAYROLL_EXPENSE, "Chi phí lương", salaryGross,
                    "Chi phí lương gồm lương cơ bản, thưởng và phụ cấp", null, null, employeeId, null));
        }
        if (net.signum() > 0) {
            lines.add(credit(ACCOUNT_COMPANY_FUND, "Tiền quỹ công ty", net,
                    "Tiền lương thực trả chuyển vào ví nhân viên", null, null, employeeId, null));
        }
        if (deduction.signum() > 0) {
            lines.add(credit(ACCOUNT_PAYROLL_DEDUCTION, "Khoản khấu trừ bảng lương", deduction,
                    "Khoản khấu trừ theo dữ liệu phiếu lương; chưa phân loại thuế hoặc bảo hiểm", null, null, employeeId, null));
        }
        for (AdvanceSettlementAllocationResponse allocation : allocations) {
            lines.add(credit(ACCOUNT_ADVANCE, "Tạm ứng nhân viên chưa quyết toán", allocation.amount(),
                    "Bù trừ tạm ứng qua lương; không phát sinh chuyển tiền hoàn", allocation.advanceBalanceId(),
                    null, employeeId, null));
        }
        saveJournal(AccountingJournalEvent.PAYROLL_SETTLEMENT, "PAYSLIP", payslip.getId(), null, null,
                employeeId, payslip.getUser().getFullName(), null, null,
                transaction == null ? null : transaction.getId(), salaryGross,
                "Hạch toán lương " + payslip.getPayslipCode(), lines);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<AccountingJournalItemResponse> getJournals(AccountingJournalEvent event, LocalDate from, LocalDate to, int page, int limit) {
        validatePage(from, to, page, limit);
        Page<AccountingJournal> result = journalRepository.findAll(
                AccountingJournalSpecification.filter(event, from, to),
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "postingDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<AccountingJournalItemResponse> items = result.getContent().stream().map(this::toItem).toList();
        return PageResponse.<AccountingJournalItemResponse>builder().items(items).total(result.getTotalElements())
                .page(page).size(limit).totalPages(result.getTotalPages()).build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountingJournalDetailResponse getJournalDetail(Long journalId) {
        AccountingJournal journal = journalRepository.findDetailById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingJournal", "id", journalId));
        BigDecimal debit = journal.getLines().stream().map(AccountingJournalLine::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = journal.getLines().stream().map(AccountingJournalLine::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AccountingJournalLineResponse> lines = journal.getLines().stream()
                .sorted(Comparator.comparing(AccountingJournalLine::getLineNumber))
                .map(line -> new AccountingJournalLineResponse(line.getId(), line.getAccountCode(), line.getAccountName(),
                        line.getDebitAmount(), line.getCreditAmount(), line.getEffectDescription(),
                        line.getAdvanceBalanceId(), line.getRequestId(), line.getEmployeeId(), line.getProjectId()))
                .toList();
        return new AccountingJournalDetailResponse(journal.getId(), journal.getJournalCode(), journal.getEventType(),
                journal.getPostingDate(), journal.getPostingPeriod(), journal.getDescription(), journal.getSourceType(),
                journal.getSourceId(), journal.getRequestId(), journal.getAdvanceBalanceId(), journal.getEmployeeId(),
                journal.getEmployeeName(), journal.getProjectId(), journal.getProjectName(), journal.getWalletTransactionId(),
                journal.getTotalAmount(), debit.compareTo(credit) == 0, debit, credit, lines);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<AdvanceEmployeeSummaryResponse> getOutstandingAdvancesByEmployee() {
        Map<Long, List<AdvanceBalance>> byEmployee = new LinkedHashMap<>();
        advanceBalanceRepository.findByStatusNotOrderByCreatedAtAscIdAsc(
                        com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus.SETTLED)
                .stream().filter(balance -> balance.getRemainingAmount().signum() > 0)
                .forEach(balance -> byEmployee.computeIfAbsent(balance.getUser().getId(), ignored -> new ArrayList<>()).add(balance));

        return byEmployee.values().stream().map(balances -> {
            var user = balances.get(0).getUser();
            List<AdvanceBalanceDetailResponse> details = balances.stream().map(this::toAdvanceDetail).toList();
            BigDecimal totalDisbursed = balances.stream().map(AdvanceBalance::getOriginalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalRemaining = balances.stream().map(AdvanceBalance::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AdvanceEmployeeSummaryResponse(user.getId(), user.getFullName(),
                    user.getDepartment() == null ? null : user.getDepartment().getName(),
                    balances.size(), totalDisbursed, totalRemaining, details);
        }).sorted(Comparator.comparing(AdvanceEmployeeSummaryResponse::totalRemaining).reversed()).toList();
    }

    private AdvanceBalanceDetailResponse toAdvanceDetail(AdvanceBalance balance) {
        Request request = balance.getAdvanceRequest();
        var project = request.getProject();
        List<AdvanceActivityResponse> activities = journalRepository.findActivitiesForAdvance(balance.getId()).stream()
                .map(journal -> new AdvanceActivityResponse(journal.getId(), journal.getJournalCode(), journal.getEventType(),
                        journal.getPostingDate(), journal.getDescription(), activityAmount(journal, balance.getId())))
                .toList();
        return new AdvanceBalanceDetailResponse(balance.getId(), request.getRequestCode(), request.getPaidAt() == null
                ? request.getCreatedAt().toLocalDate() : request.getPaidAt().toLocalDate(),
                project == null ? null : project.getId(), project == null ? null : project.getName(),
                request.getPhase() == null ? null : request.getPhase().getName(),
                request.getCategory() == null ? null : request.getCategory().getName(), balance.getOriginalAmount(),
                balance.getReimbursedAmount(), balance.getCashReturnedAmount(), balance.getPayrollOffsetAmount(),
                balance.getLegacyUnclassifiedAmount(), balance.getRemainingAmount(), balance.getStatus(), activities);
    }

    private BigDecimal activityAmount(AccountingJournal journal, Long advanceBalanceId) {
        if (journal.getAdvanceBalanceId() != null && journal.getAdvanceBalanceId().equals(advanceBalanceId)) return journal.getTotalAmount();
        return journal.getLines().stream().filter(line -> advanceBalanceId.equals(line.getAdvanceBalanceId()))
                .map(line -> line.getDebitAmount().max(line.getCreditAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AccountingJournalItemResponse toItem(AccountingJournal journal) {
        return new AccountingJournalItemResponse(journal.getId(), journal.getJournalCode(), journal.getEventType(),
                journal.getPostingDate(), journal.getPostingPeriod(), journal.getDescription(), journal.getSourceType(),
                journal.getSourceId(), journal.getRequestId(), journal.getAdvanceBalanceId(), journal.getEmployeeId(),
                journal.getEmployeeName(), journal.getProjectId(), journal.getProjectName(), journal.getWalletTransactionId(),
                journal.getTotalAmount(), true);
    }

    private AccountingJournal saveJournal(AccountingJournalEvent event, String sourceType, Long uniqueSourceId,
            Long requestId, Long advanceBalanceId, Long employeeId, String employeeName, Long projectId,
            String projectName, Long walletTransactionId, BigDecimal totalAmount, String description, List<LineDraft> drafts) {
        String sourceId = String.valueOf(uniqueSourceId);
        var existing = journalRepository.findByEventTypeAndSourceTypeAndSourceId(event, sourceType, sourceId);
        if (existing.isPresent()) return existing.get();

        BigDecimal debit = drafts.stream().map(LineDraft::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = drafts.stream().map(LineDraft::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debit.compareTo(credit) != 0) throw new BadRequestException("Refusing to save an unbalanced accounting journal");
        if (totalAmount == null || totalAmount.signum() <= 0) throw new BadRequestException("Journal amount must be positive");

        LocalDate postingDate = LocalDate.now();
        AccountingJournal journal = AccountingJournal.builder()
                .journalCode("JRN-" + postingDate.toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .eventType(event).postingDate(postingDate).postingPeriod(YearMonth.from(postingDate).toString())
                .description(description).sourceType(sourceType).sourceId(sourceId).requestId(requestId)
                .advanceBalanceId(advanceBalanceId).employeeId(employeeId).employeeName(employeeName)
                .projectId(projectId).projectName(projectName).walletTransactionId(walletTransactionId)
                .totalAmount(totalAmount).build();
        for (int i = 0; i < drafts.size(); i++) {
            LineDraft draft = drafts.get(i);
            journal.getLines().add(AccountingJournalLine.builder().journal(journal).lineNumber(i + 1)
                    .accountCode(draft.accountCode()).accountName(draft.accountName())
                    .debitAmount(draft.debit()).creditAmount(draft.credit()).effectDescription(draft.effect())
                    .advanceBalanceId(draft.advanceBalanceId()).requestId(draft.requestId())
                    .employeeId(draft.employeeId()).projectId(draft.projectId()).build());
        }
        return journalRepository.save(journal);
    }

    private LineDraft debit(String code, String name, BigDecimal amount, String effect,
            Long advanceId, Long requestId, Long employeeId, Long projectId) {
        return new LineDraft(code, name, amount, BigDecimal.ZERO, effect, advanceId, requestId, employeeId, projectId);
    }

    private LineDraft credit(String code, String name, BigDecimal amount, String effect,
            Long advanceId, Long requestId, Long employeeId, Long projectId) {
        return new LineDraft(code, name, BigDecimal.ZERO, amount, effect, advanceId, requestId, employeeId, projectId);
    }

    private void validatePage(LocalDate from, LocalDate to, int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) throw new BadRequestException("Invalid page or limit");
        if (from != null && to != null && from.isAfter(to)) throw new BadRequestException("from must be before or equal to to");
    }

    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private record LineDraft(String accountCode, String accountName, BigDecimal debit, BigDecimal credit,
            String effect, Long advanceBalanceId, Long requestId, Long employeeId, Long projectId) {}
}
