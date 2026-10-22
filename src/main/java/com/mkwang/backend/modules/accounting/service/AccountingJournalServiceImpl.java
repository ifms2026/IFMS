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
import com.mkwang.backend.modules.request.repository.RequestRepository;
import com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse;
import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudget;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.project.repository.ExpenseCategoryRepository;
import com.mkwang.backend.modules.project.repository.PhaseCategoryBudgetRepository;
import com.mkwang.backend.modules.project.repository.ProjectPhaseRepository;
import com.mkwang.backend.modules.project.repository.ProjectRepository;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.mkwang.backend.modules.user.entity.User;

@Service
@RequiredArgsConstructor
public class AccountingJournalServiceImpl implements AccountingJournalService {
    private static final String ACCOUNT_ADVANCE = "ADVANCE_RECEIVABLE";
    private static final String ACCOUNT_EXPENSE = "PROJECT_EXPENSE";
    private static final String ACCOUNT_EMPLOYEE_PAYABLE = "EMPLOYEE_REIMBURSEMENT_PAYABLE";
    private static final String ACCOUNT_PAYROLL_EXPENSE = "PAYROLL_EXPENSE";
    private static final String ACCOUNT_PAYROLL_DEDUCTION = "PAYROLL_DEDUCTION_CLEARING";
    private static final String ACCOUNT_COMPANY_FUND = "COMPANY_FUND_WALLET";
    private static final String ACCOUNT_EXTERNAL_BANK = "EXTERNAL_BANK_CASH";
    private static final String ACCOUNT_DEPARTMENT_WALLET = "DEPARTMENT_WALLET";
    private static final String ACCOUNT_PROJECT_WALLET = "PROJECT_WALLET";

    private final AccountingJournalRepository journalRepository;
    private final com.mkwang.backend.modules.accounting.repository.PayslipRepository payslipRepository;
    private final com.mkwang.backend.modules.wallet.service.WalletFinancialEvidenceService walletEvidence;
    private final com.mkwang.backend.modules.accounting.mapper.AccountingJournalMapper journalMapper;
    private final com.mkwang.backend.modules.request.service.RequestAccountingContextService contextService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.mkwang.backend.modules.wallet.service.WalletService walletService;
    private final AdvanceBalanceRepository advanceBalanceRepository;
    private final RequestRepository requestRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final PhaseCategoryBudgetRepository phaseCategoryBudgetRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final com.mkwang.backend.modules.wallet.repository.WalletRepository walletRepository;

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
    public void recordSystemTopup(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        String description = "Nạp tiền từ nguồn ngân hàng vào quỹ công ty";
        if (transaction.getPaymentRef() != null && !transaction.getPaymentRef().isBlank()) {
            description += " · mã đối soát " + transaction.getPaymentRef();
        }
        saveJournal(AccountingJournalEvent.SYSTEM_TOPUP, "TRANSACTION", transaction.getId(), null, null,
                null, null, null, null, transaction.getId(), amount,
                transaction.getDescription() == null || transaction.getDescription().isBlank()
                        ? description : transaction.getDescription() + " · " + description,
                List.of(
                        debit(ACCOUNT_COMPANY_FUND, "Quỹ công ty", amount,
                                "Tiền vào ví quỹ công ty tăng", null, null, null, null),
                        credit(ACCOUNT_EXTERNAL_BANK, "Tiền tại ngân hàng/nguồn bên ngoài", amount,
                                "Tiền tại nguồn bên ngoài giảm khi chuyển vào quỹ IFMS", null, null, null, null)));
    }

    @Override
    @Transactional
    public void recordDepartmentAllocation(Request request, Transaction transaction, String departmentName) {
        BigDecimal amount = transaction.getAmount();
        Long projectId = null;
        String requestCode = request.getRequestCode();
        saveJournal(AccountingJournalEvent.DEPARTMENT_ALLOCATION, "REQUEST", request.getId(), request.getId(), null,
                request.getRequester().getId(), request.getRequester().getFullName(), projectId, null,
                transaction.getId(), amount, "Cấp ngân sách cho phòng ban " + departmentName + " · " + requestCode,
                List.of(
                        debit(ACCOUNT_DEPARTMENT_WALLET, "Quỹ phòng ban: " + departmentName, amount,
                                "Số dư quỹ phòng ban tăng do được cấp ngân sách", null, request.getId(),
                                request.getRequester().getId(), null),
                        credit(ACCOUNT_COMPANY_FUND, "Quỹ công ty", amount,
                                "Số dư quỹ công ty giảm khi phân bổ ngân sách nội bộ", null, request.getId(),
                                request.getRequester().getId(), null)));
    }

    @Override
    @Transactional
    public void recordProjectAllocation(Request request, Transaction transaction, String departmentName) {
        BigDecimal amount = transaction.getAmount();
        Long projectId = request.getProject().getId();
        String projectName = request.getProject().getName();
        saveJournal(AccountingJournalEvent.PROJECT_ALLOCATION, "REQUEST", request.getId(), request.getId(), null,
                request.getRequester().getId(), request.getRequester().getFullName(), projectId, projectName,
                transaction.getId(), amount, "Cấp vốn dự án " + projectName + " từ phòng ban " + departmentName
                        + " · " + request.getRequestCode(),
                List.of(
                        debit(ACCOUNT_PROJECT_WALLET, "Quỹ dự án: " + projectName, amount,
                                "Số dư quỹ dự án tăng do nhận phân bổ nội bộ", null, request.getId(),
                                request.getRequester().getId(), projectId),
                        credit(ACCOUNT_DEPARTMENT_WALLET, "Quỹ phòng ban: " + departmentName, amount,
                                "Số dư quỹ phòng ban giảm khi cấp vốn cho dự án", null, request.getId(),
                                request.getRequester().getId(), projectId)));
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
    public PageResponse<AccountingJournalItemResponse> getJournals(AccountingJournalEvent event, LocalDate from, LocalDate to,
            Long employeeId, Long projectId, Long requestId, int page, int limit) {
        validatePage(from, to, page, limit);
        Page<AccountingJournal> result = journalRepository.findAll(
                AccountingJournalSpecification.filter(event, from, to, employeeId, projectId, requestId),
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "postingDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<AccountingJournalItemResponse> items = result.getContent().stream().map(journalMapper::toItem).toList();
        return PageResponse.<AccountingJournalItemResponse>builder().items(items).total(result.getTotalElements())
                .page(page).size(limit).totalPages(result.getTotalPages()).build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountingJournalDetailResponse getJournalDetail(Long journalId) {
        AccountingJournal journal = journalRepository.findDetailById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingJournal", "id", journalId));
        return journalMapper.toDetail(journal);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<AdvanceEmployeeSummaryResponse> getOutstandingAdvancesByEmployee() {
        List<AdvanceBalance> openBalances = advanceBalanceRepository.findByStatusNotOrderByCreatedAtAscIdAsc(
                        com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus.SETTLED)
                .stream().filter(balance -> balance.getRemainingAmount().signum() > 0).toList();
        return advanceSummaries(openBalances);
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<AdvanceEmployeeSummaryResponse> getOutstandingPage(String search,int page,int limit) {
        checkPage(page,limit);
        var users=advanceBalanceRepository.findOutstandingEmployeeIds("%"+(search==null?"":search.trim().toLowerCase(java.util.Locale.ROOT))+"%",org.springframework.data.domain.PageRequest.of(page-1,limit));
        var balances=users.isEmpty()?List.<AdvanceBalance>of():advanceBalanceRepository.findByUser_IdInAndRemainingAmountGreaterThanAndStatusNotOrderByCreatedAtAscIdAsc(users.getContent(),BigDecimal.ZERO,com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus.SETTLED);
        return PageResponse.<AdvanceEmployeeSummaryResponse>builder().items(advanceSummaries(balances)).total(users.getTotalElements()).page(page).size(limit).totalPages(users.getTotalPages()).build();
    }
    private void checkPage(int page,int limit){if(page<1||limit<1||limit>100)throw new BadRequestException("Phân trang không hợp lệ");}
    private List<AdvanceEmployeeSummaryResponse> advanceSummaries(List<AdvanceBalance> openBalances) {
        Map<Long, List<AccountingJournal>> activityByAdvance = new HashMap<>();
        if (!openBalances.isEmpty()) {
            List<Long> advanceIds = openBalances.stream().map(AdvanceBalance::getId).toList();
            for (AccountingJournal journal : journalRepository.findActivitiesForAdvances(advanceIds)) {
                Set<Long> affected = new LinkedHashSet<>();
                if (journal.getAdvanceBalanceId() != null) affected.add(journal.getAdvanceBalanceId());
                journal.getLines().stream().map(AccountingJournalLine::getAdvanceBalanceId)
                        .filter(Objects::nonNull).forEach(affected::add);
                for (Long advanceId : affected) if (advanceIds.contains(advanceId)) {
                    activityByAdvance.computeIfAbsent(advanceId, ignored -> new ArrayList<>()).add(journal);
                }
            }
        }
        Map<Long, List<AdvanceBalance>> byEmployee = new LinkedHashMap<>();
        openBalances.forEach(balance -> byEmployee.computeIfAbsent(balance.getUser().getId(), ignored -> new ArrayList<>()).add(balance));

        return byEmployee.values().stream().map(balances -> {
            var user = balances.get(0).getUser();
            List<AdvanceBalanceDetailResponse> details = balances.stream()
                    .map(balance -> toAdvanceDetail(balance, activityByAdvance.getOrDefault(balance.getId(), List.of()))).toList();
            BigDecimal totalDisbursed = balances.stream().map(AdvanceBalance::getOriginalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalRemaining = balances.stream().map(AdvanceBalance::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AdvanceEmployeeSummaryResponse(user.getId(), user.getFullName(),
                    user.getDepartment() == null ? null : user.getDepartment().getName(),
                    balances.size(), totalDisbursed, totalRemaining, details);
        }).sorted(Comparator.comparing(AdvanceEmployeeSummaryResponse::totalRemaining).reversed()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<LedgerProjectBudgetResponse> getBudgetExposure() {
        return budgetDetails(projectRepository.findAllByOrderByCreatedAtDesc());
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<LedgerProjectBudgetResponse> getBudgetPage(String search,int page,int limit) {
        checkPage(page,limit);
        var spec=com.mkwang.backend.modules.accounting.repository.AccountingProjectSpecification.filter(search);
        var projects=projectRepository.findAll(spec,org.springframework.data.domain.PageRequest.of(page-1,limit,org.springframework.data.domain.Sort.by("createdAt").descending().and(org.springframework.data.domain.Sort.by("id"))));
        return PageResponse.<LedgerProjectBudgetResponse>builder().items(budgetDetails(projects.getContent())).total(projects.getTotalElements()).page(page).size(limit).totalPages(projects.getTotalPages()).build();
    }
    private List<LedgerProjectBudgetResponse> budgetDetails(List<Project> projectList) {
        if (projectList.isEmpty()) return List.of();
        List<Long> projectIds = projectList.stream().map(Project::getId).toList();
        Map<ScopeKey, BigDecimal> lockedByScope = new LinkedHashMap<>();
        for (Object[] row : requestRepository.sumActiveReservationsForLedgerProjects(projectIds)) {
            ScopeKey key = new ScopeKey(((Number) row[0]).longValue(),
                    row[1] == null ? null : ((Number) row[1]).longValue(),
                    row[2] == null ? null : ((Number) row[2]).longValue());
            lockedByScope.put(key, nz((BigDecimal) row[3]));
        }

        Map<ScopeKey, BigDecimal> advancesByScope = new LinkedHashMap<>();
        for (Object[] row : advanceBalanceRepository.sumOutstandingByProjectPhaseCategoryForProjects(projectIds)) {
            ScopeKey key = new ScopeKey(((Number)row[0]).longValue(),
                    row[1]==null?null:((Number)row[1]).longValue(),
                    row[2]==null?null:((Number)row[2]).longValue());
            advancesByScope.put(key,nz((BigDecimal)row[3]));
        }

        List<ProjectPhase> allPhases=projectIds.isEmpty()?List.of():projectPhaseRepository.findByProject_IdInOrderByProject_IdAscCreatedAtAsc(projectIds);
        Map<Long,List<ProjectPhase>> phasesByProject=new LinkedHashMap<>();
        allPhases.forEach(phase->phasesByProject.computeIfAbsent(phase.getProject().getId(),ignored->new ArrayList<>()).add(phase));
        List<PhaseCategoryBudget> allBudgets=projectIds.isEmpty()?List.of():phaseCategoryBudgetRepository.findByPhase_Project_IdIn(projectIds);
        Map<Long,Map<Long,PhaseCategoryBudget>> budgetsByPhase=new LinkedHashMap<>();
        Set<Long> allCategoryIds=new LinkedHashSet<>();
        allBudgets.forEach(budget->{budgetsByPhase.computeIfAbsent(budget.getPhase().getId(),ignored->new LinkedHashMap<>())
                .put(budget.getCategory().getId(),budget);allCategoryIds.add(budget.getCategory().getId());});
        lockedByScope.keySet().stream().map(ScopeKey::categoryId).filter(Objects::nonNull).forEach(allCategoryIds::add);
        advancesByScope.keySet().stream().map(ScopeKey::categoryId).filter(Objects::nonNull).forEach(allCategoryIds::add);
        Map<Long,ExpenseCategory> categoriesById=new LinkedHashMap<>();
        if(!allCategoryIds.isEmpty())expenseCategoryRepository.findAllById(allCategoryIds)
                .forEach(category->categoriesById.put(category.getId(),category));
        Map<Long,BigDecimal> walletBalances=new HashMap<>();
        if(!projectIds.isEmpty())walletRepository.findBalancesByOwnerTypeAndOwnerIds(
                com.mkwang.backend.modules.wallet.entity.WalletOwnerType.PROJECT,projectIds)
                .forEach(row->walletBalances.put(((Number)row[0]).longValue(),nz((BigDecimal)row[1])));

        List<LedgerProjectBudgetResponse> result = new ArrayList<>();
        for (Project project : projectList) {
            Long projectId = project.getId();
            BigDecimal projectLocked = sumForProject(lockedByScope, projectId);
            BigDecimal projectAdvances = sumForProject(advancesByScope, projectId);
            List<LedgerPhaseBudgetResponse> phases = new ArrayList<>();
            for (ProjectPhase phase : phasesByProject.getOrDefault(projectId,List.of())) {
                Long phaseId = phase.getId();
                List<LedgerCategoryBudgetResponse> categories = new ArrayList<>();
                Map<Long, PhaseCategoryBudget> categoryBudgets = budgetsByPhase.getOrDefault(phaseId,Map.of());
                Set<Long> categoryIds = new LinkedHashSet<>(categoryBudgets.keySet());
                addCategoryIds(categoryIds, lockedByScope, projectId, phaseId);
                addCategoryIds(categoryIds, advancesByScope, projectId, phaseId);
                for (Long categoryId : categoryIds) {
                    ScopeKey scope = new ScopeKey(projectId, phaseId, categoryId);
                    PhaseCategoryBudget categoryBudget = categoryBudgets.get(categoryId);
                    ExpenseCategory category = categoriesById.get(categoryId);
                    if (category == null) continue;
                    categories.add(new LedgerCategoryBudgetResponse(categoryId, category.getName(),
                            categoryBudget == null ? BigDecimal.ZERO : nz(categoryBudget.getBudgetLimit()),
                            categoryBudget == null ? BigDecimal.ZERO : nz(categoryBudget.getCurrentSpent()),
                            lockedByScope.getOrDefault(scope, BigDecimal.ZERO),
                            advancesByScope.getOrDefault(scope, BigDecimal.ZERO)));
                }
                BigDecimal phaseLocked = sumForPhase(lockedByScope, projectId, phaseId);
                BigDecimal phaseAdvances = sumForPhase(advancesByScope, projectId, phaseId);
                phases.add(new LedgerPhaseBudgetResponse(phaseId, phase.getName(), nz(phase.getBudgetLimit()),
                        nz(phase.getCurrentSpent()), phaseLocked, phaseAdvances, categories));
            }
            result.add(new LedgerProjectBudgetResponse(projectId, project.getProjectCode(), project.getName(),
                    nz(project.getTotalBudget()), walletBalances.getOrDefault(projectId,BigDecimal.ZERO), nz(project.getTotalSpent()),
                    projectLocked, projectAdvances, phases));
        }
        return result;
    }

    private BigDecimal sumForProject(Map<ScopeKey, BigDecimal> values, Long projectId) {
        return values.entrySet().stream().filter(entry -> entry.getKey().projectId().equals(projectId))
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumForPhase(Map<ScopeKey, BigDecimal> values, Long projectId, Long phaseId) {
        return values.entrySet().stream().filter(entry -> entry.getKey().projectId().equals(projectId)
                        && java.util.Objects.equals(entry.getKey().phaseId(), phaseId))
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addCategoryIds(Set<Long> categoryIds, Map<ScopeKey, BigDecimal> values, Long projectId, Long phaseId) {
        values.keySet().stream().filter(key -> key.projectId().equals(projectId)
                        && java.util.Objects.equals(key.phaseId(), phaseId) && key.categoryId() != null)
                .map(ScopeKey::categoryId).forEach(categoryIds::add);
    }

    private AdvanceBalanceDetailResponse toAdvanceDetail(AdvanceBalance balance, List<AccountingJournal> journals) {
        Request request = balance.getAdvanceRequest();
        var project = request.getProject();
        List<AdvanceActivityResponse> activities = journals.stream()
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

    private AccountingJournal saveJournal(AccountingJournalEvent event, String sourceType, Long uniqueSourceId,
            Long requestId, Long advanceBalanceId, Long employeeId, String employeeName, Long projectId,
            String projectName, Long walletTransactionId, BigDecimal totalAmount, String description, List<LineDraft> drafts) {
        String sourceId = String.valueOf(uniqueSourceId);
        var existing = journalRepository.findByEventTypeAndSourceTypeAndSourceId(event, sourceType, sourceId);
        if (existing.isPresent()) return existing.get();

        BigDecimal debit = drafts.stream().map(LineDraft::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = drafts.stream().map(LineDraft::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount == null || totalAmount.signum() <= 0) throw new BadRequestException("Journal amount must be positive");
        if (drafts.size()<2 || debit.compareTo(totalAmount)!=0 || debit.compareTo(credit) != 0) throw new BadRequestException("Refusing to save an unbalanced accounting journal");

        var historical = AccountingPostingContext.current();
        LocalDate postingDate = historical == null ? LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")) : historical.postingDate();
        var context = requestId != null ? contextService.forRequest(requestId)
                : advanceBalanceId != null ? contextService.forAdvance(advanceBalanceId) : null;
        String snapshot;
        Object sourceSnapshot = context;
        if ("PAYSLIP".equals(sourceType)) {
            var payslip = payslipRepository.findById(uniqueSourceId).orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
            var values = new LinkedHashMap<String,Object>();
            values.put("payslipId",payslip.getId()); values.put("payslipCode",payslip.getPayslipCode());
            values.put("periodId",payslip.getPeriod().getId()); values.put("periodCode",payslip.getPeriod().getPeriodCode());
            values.put("employeeId",payslip.getUser().getId()); values.put("employeeName",payslip.getUser().getFullName());
            values.put("baseSalary",payslip.getBaseSalary()); values.put("bonus",payslip.getBonus());
            values.put("allowance",payslip.getAllowance()); values.put("deduction",payslip.getDeduction());
            values.put("advanceDeduct",payslip.getAdvanceDeduct()); values.put("finalNetSalary",payslip.getFinalNetSalary());
            values.put("replacesPayslipId",payslip.getReplacesPayslipId());sourceSnapshot=values;
        } else if (sourceSnapshot==null && walletTransactionId!=null) sourceSnapshot=walletEvidence.get(walletTransactionId);
        if (historical != null) {
            var evidence = new LinkedHashMap<String,Object>();evidence.put("source",sourceSnapshot);
            evidence.put("backfillEvidenceRef",historical.evidenceRef());evidence.put("backfilledAt",Instant.now());sourceSnapshot=evidence;
        }
        try { snapshot = sourceSnapshot==null?null:objectMapper.writeValueAsString(sourceSnapshot); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new com.mkwang.backend.common.exception.InternalSystemException("Unable to snapshot accounting source");
        }
        ActorSnapshot actor = currentActor();
        AccountingJournal journal = AccountingJournal.builder()
                .journalCode("JRN-" + postingDate.toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .eventType(event).postingDate(postingDate).postingPeriod(YearMonth.from(postingDate).toString())
                .description(description).sourceType(sourceType).sourceId(sourceId).requestId(requestId)
                .advanceBalanceId(advanceBalanceId).employeeId(employeeId).employeeName(employeeName)
                .projectId(projectId).projectName(projectName).walletTransactionId(walletTransactionId)
                .totalAmount(totalAmount).createdByUserId(actor.userId()).createdByName(actor.name())
                .createdAt(Instant.now()).businessDate(historical==null?postingDate:historical.businessDate())
                .departmentId(context == null ? null : context.departmentId())
                .departmentName(context == null ? null : context.departmentName())
                .phaseId(context == null ? null : context.phaseId()).phaseName(context == null ? null : context.phaseName())
                .categoryId(context == null ? null : context.categoryId()).categoryName(context == null ? null : context.categoryName())
                .sourceSnapshot(snapshot).build();
        for (int i = 0; i < drafts.size(); i++) {
            LineDraft draft = drafts.get(i);
            var lineContext = draft.advanceBalanceId() != null ? contextService.forAdvance(draft.advanceBalanceId()) : context;
            journal.getLines().add(AccountingJournalLine.builder().journal(journal).lineNumber(i + 1)
                    .accountCode(draft.accountCode()).accountName(draft.accountName())
                    .debitAmount(draft.debit()).creditAmount(draft.credit()).effectDescription(draft.effect())
                    .advanceBalanceId(draft.advanceBalanceId()).requestId(draft.requestId())
                    .employeeId(draft.employeeId()).projectId(lineContext == null ? draft.projectId() : lineContext.projectId())
                    .departmentId(lineContext == null ? null : lineContext.departmentId())
                    .phaseId(lineContext == null ? null : lineContext.phaseId())
                    .categoryId(lineContext == null ? null : lineContext.categoryId())
                    .projectName(lineContext == null ? null : lineContext.projectName())
                    .phaseName(lineContext == null ? null : lineContext.phaseName())
                    .categoryName(lineContext == null ? null : lineContext.categoryName()).build());
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

    private ActorSnapshot currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return new ActorSnapshot(null, "system");
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.mkwang.backend.modules.auth.security.UserDetailsAdapter adapter) {
            User user = adapter.getUser();
            return new ActorSnapshot(user.getId(), user.getFullName());
        }
        if (principal instanceof User user) return new ActorSnapshot(user.getId(), user.getFullName());
        String name = authentication.getName();
        return new ActorSnapshot(null, name == null || name.isBlank() || "anonymousUser".equals(name) ? "system" : name);
    }

    private record LineDraft(String accountCode, String accountName, BigDecimal debit, BigDecimal credit,
            String effect, Long advanceBalanceId, Long requestId, Long employeeId, Long projectId) {}
    private record ActorSnapshot(Long userId, String name) {}
    private record ScopeKey(Long projectId, Long phaseId, Long categoryId) {}
}
