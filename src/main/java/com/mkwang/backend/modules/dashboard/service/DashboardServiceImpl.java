package com.mkwang.backend.modules.dashboard.service;

import com.mkwang.backend.modules.accounting.entity.PayrollPeriod;
import com.mkwang.backend.modules.accounting.service.PayrollManagementService;
import com.mkwang.backend.modules.audit.dto.response.AuditLogResponse;
import com.mkwang.backend.modules.audit.service.AuditLogService;
import com.mkwang.backend.modules.dashboard.dto.response.AccountantDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CashFlowAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CfoDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.EmployeeSpendingAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.ManagerDashboardResponse;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.organization.repository.DepartmentRepository;
import com.mkwang.backend.modules.organization.service.DepartmentService;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.service.ManagerProjectService;
import com.mkwang.backend.modules.request.dto.response.CfoDeptTopupItemResponse;
import com.mkwang.backend.modules.request.repository.AdvanceBalanceRepository;
import com.mkwang.backend.modules.request.service.RequestService;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.service.UserService;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.repository.LedgerEntryRepository;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final UserService userService;
    private final ManagerProjectService managerProjectService;
    private final RequestService requestService;
    private final WalletService walletService;
    private final PayrollManagementService payrollManagementService;
    private final AuditLogService auditLogService;
    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AdvanceBalanceRepository advanceBalanceRepository;
    private final com.mkwang.backend.modules.request.repository.RequestRepository requestRepository;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_DEPT')")
    public ManagerDashboardResponse getManagerDashboard(Long managerId) {
        User manager = userService.getUserById(managerId);
        Long deptId = manager.getDepartment().getId();

        BigDecimal[] budgetSnapshot = managerProjectService.getDeptBudgetSnapshot(managerId);
        BigDecimal totalQuota     = budgetSnapshot[0];
        BigDecimal totalAvailable = budgetSnapshot[1];
        BigDecimal totalSpent     = totalQuota.subtract(totalAvailable);

        Map<ProjectStatus, Long> statusCounts = managerProjectService.getDeptProjectStatusCounts(managerId);

        long pendingApprovals = requestService.countDeptPendingProjectTopup(deptId);

        BigDecimal totalDebt       = requestService.sumDeptOutstandingAdvanceDebt(deptId);
        long employeesWithDebt     = requestService.countDeptEmployeesWithDebt(deptId);

        return ManagerDashboardResponse.builder()
                .departmentBudget(ManagerDashboardResponse.DepartmentBudget.builder()
                        .totalProjectQuota(totalQuota)
                        .totalAvailableBalance(totalAvailable)
                        .totalSpent(totalSpent)
                        .build())
                .projectStatusSummary(ManagerDashboardResponse.ProjectStatusSummary.builder()
                        .active(statusCounts.getOrDefault(ProjectStatus.ACTIVE, 0L))
                        .planning(statusCounts.getOrDefault(ProjectStatus.PLANNING, 0L))
                        .paused(statusCounts.getOrDefault(ProjectStatus.PAUSED, 0L))
                        .closed(statusCounts.getOrDefault(ProjectStatus.CLOSED, 0L))
                        .build())
                .pendingApprovalsCount(pendingApprovals)
                .teamDebtSummary(ManagerDashboardResponse.TeamDebtSummary.builder()
                        .totalDebt(totalDebt)
                        .employeesWithDebt(employeesWithDebt)
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountantDashboardResponse getAccountantDashboard() {
        BigDecimal fundBalance = walletService.getWallet(WalletOwnerType.COMPANY_FUND, 1L).getBalance();

        long pendingDisbursements = requestService.countPendingDisbursements();

        LocalDate now = LocalDate.now();
        BigDecimal monthlyInflow  = walletService.getCompanyFundMonthlyInflow(now.getYear(), now.getMonthValue());
        BigDecimal monthlyOutflow = walletService.getCompanyFundMonthlyOutflow(now.getYear(), now.getMonthValue());

        Optional<PayrollPeriod> latestPeriod = payrollManagementService.getLatestPayrollPeriod();
        AccountantDashboardResponse.PayrollStatusSnapshot payrollSnapshot = latestPeriod
                .map(p -> AccountantDashboardResponse.PayrollStatusSnapshot.builder()
                        .latestPeriod(p.getName())
                        .status(p.getStatus())
                        .build())
                .orElse(null);

        return AccountantDashboardResponse.builder()
                .systemFundBalance(fundBalance)
                .pendingDisbursementsCount(pendingDisbursements)
                .monthlyInflow(monthlyInflow)
                .monthlyOutflow(monthlyOutflow)
                .payrollStatus(payrollSnapshot)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_APPROVE_DEPT_TOPUP')")
    public CfoDashboardResponse getCfoDashboard() {
        BigDecimal fundBalance = walletService.getWallet(WalletOwnerType.COMPANY_FUND, 1L).getBalance();

        long pendingApprovals = requestService.countPendingDeptTopup();

        LocalDate now = LocalDate.now();
        BigDecimal monthlyApproved = requestService.sumMonthlyApprovedDeptTopup(now.getYear(), now.getMonthValue());
        long monthlyRejected       = requestService.countMonthlyRejectedDeptTopup(now.getYear(), now.getMonthValue());

        List<CfoDeptTopupItemResponse> recent = requestService.getRecentDeptTopups(5);
        List<CfoDashboardResponse.RecentApprovalItem> recentApprovals = recent.stream()
                .map(item -> CfoDashboardResponse.RecentApprovalItem.builder()
                        .id(item.id())
                        .requestCode(item.requestCode())
                        .departmentName(item.departmentName())
                        .amount(item.amount())
                        .status(item.status())
                        .createdAt(item.createdAt())
                        .build())
                .toList();

        return CfoDashboardResponse.builder()
                .companyFundBalance(fundBalance)
                .pendingApprovalsCount(pendingApprovals)
                .monthlyApprovedAmount(monthlyApproved)
                .monthlyRejectedCount(monthlyRejected)
                .recentApprovals(recentApprovals)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_VIEW_LIST')")
    public AdminDashboardResponse getAdminDashboard() {
        long totalUsers       = userService.countActiveUsers();
        long totalDepartments = departmentService.countDepartments();
        BigDecimal totalWalletBalance = walletService.sumBalancesByType(WalletOwnerType.USER);

        List<AdminDashboardResponse.RecentAuditEvent> recentEvents = auditLogService
                .getAuditLogs(null, null, null, null, null, 1, 5)
                .getItems()
                .stream()
                .map(log -> AdminDashboardResponse.RecentAuditEvent.builder()
                        .id(log.id())
                        .actorName(log.actorName())
                        .action(log.action())
                        .entityName(log.entityName())
                        .createdAt(log.createdAt())
                        .build())
                .toList();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalDepartments(totalDepartments)
                .totalWalletBalance(totalWalletBalance)
                .recentAuditEvents(recentEvents)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('PAYROLL_MANAGE', 'USER_VIEW_LIST')")
    public CashFlowAnalyticsResponse getCashFlowAnalytics(String period, String unit) {
        List<int[]> months = resolveMonths(period);
        boolean inMillions = "million".equalsIgnoreCase(unit);

        List<CashFlowAnalyticsResponse.CashFlowPoint> points = new ArrayList<>();
        BigDecimal totalInflow  = BigDecimal.ZERO;
        BigDecimal totalOutflow = BigDecimal.ZERO;

        for (int[] ym : months) {
            int year = ym[0];
            int month = ym[1];
            BigDecimal inflow  = walletService.getCompanyFundMonthlyInflow(year, month);
            BigDecimal outflow = walletService.getCompanyFundMonthlyOutflow(year, month);

            if (inMillions) {
                inflow  = inflow.divide(MILLION, 2, RoundingMode.HALF_UP);
                outflow = outflow.divide(MILLION, 2, RoundingMode.HALF_UP);
            }

            totalInflow  = totalInflow.add(inflow);
            totalOutflow = totalOutflow.add(outflow);
            points.add(CashFlowAnalyticsResponse.CashFlowPoint.builder()
                    .label(buildLabel(year, month, period))
                    .inflow(inflow)
                    .outflow(outflow)
                    .build());
        }

        return CashFlowAnalyticsResponse.builder()
                .period(period)
                .points(points)
                .totalInflow(totalInflow)
                .totalOutflow(totalOutflow)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_VIEW_LIST')")
    public AdminAnalyticsResponse getAdminAnalytics() {
        LocalDate now     = LocalDate.now();
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = now.atTime(LocalTime.MAX);

        // ── Dept spending (MTD DEBIT on DEPARTMENT wallets) ─────────
        List<Object[]> rawSpending = ledgerEntryRepository
                .sumDebitGroupedByOwnerAndRange(WalletOwnerType.DEPARTMENT, from, to);

        Map<Long, BigDecimal> spendByDeptId = rawSpending.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));

        List<Department> allDepts = departmentRepository.findAll();
        List<AdminAnalyticsResponse.DeptSpendingItem> deptSpending = allDepts.stream()
                .filter(d -> spendByDeptId.containsKey(d.getId()))
                .sorted((a, b) -> spendByDeptId.get(b.getId()).compareTo(spendByDeptId.get(a.getId())))
                .map(d -> AdminAnalyticsResponse.DeptSpendingItem.builder()
                        .deptId(d.getId())
                        .deptName(d.getName())
                        .spent(spendByDeptId.get(d.getId()))
                        .build())
                .collect(Collectors.toList());

        // ── Top debtors (system-wide outstanding advance balance) ────
        List<Object[]> rawDebtors = advanceBalanceRepository
                .findTopDebtorsSystemWide(PageRequest.of(0, 10));

        LocalDateTime nowDt = LocalDateTime.now();
        List<AdminAnalyticsResponse.TopDebtorItem> topDebtors = rawDebtors.stream()
                .map(row -> {
                    Long    userId    = (Long)          row[0];
                    String  name      = (String)        row[1];
                    String  dept      = row[2] != null ? (String) row[2] : "—";
                    BigDecimal amount = (BigDecimal)    row[3];
                    LocalDateTime oldest = (LocalDateTime) row[4];
                    long days = oldest != null ? ChronoUnit.DAYS.between(oldest, nowDt) : 0;
                    return AdminAnalyticsResponse.TopDebtorItem.builder()
                            .userId(userId)
                            .fullName(name)
                            .deptName(dept)
                            .outstandingAmount(amount)
                            .daysSinceDisbursement(days)
                            .build();
                })
                .collect(Collectors.toList());

        return AdminAnalyticsResponse.builder()
                .deptSpending(deptSpending)
                .topDebtors(topDebtors)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_SELF')")
    public EmployeeSpendingAnalyticsResponse getEmployeeSpendingAnalytics(Long userId) {
        LocalDate now = LocalDate.now();

        // Build last-6-months window
        List<int[]> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = now.minusMonths(i);
            months.add(new int[]{d.getYear(), d.getMonthValue()});
        }
        LocalDateTime from = now.minusMonths(5).withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = now.atTime(LocalTime.MAX);

        List<com.mkwang.backend.modules.request.entity.RequestType> types = List.of(
                com.mkwang.backend.modules.request.entity.RequestType.ADVANCE,
                com.mkwang.backend.modules.request.entity.RequestType.EXPENSE,
                com.mkwang.backend.modules.request.entity.RequestType.REIMBURSE
        );

        List<Object[]> rows = requestRepository.sumPaidByMonthAndType(
                userId, com.mkwang.backend.modules.request.entity.RequestStatus.PAID, types, from, to);

        // Map (year_month_type) -> BigDecimal
        Map<String, BigDecimal> dataMap = new java.util.HashMap<>();
        for (Object[] row : rows) {
            int year  = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            String type = row[2].toString();
            BigDecimal sum = (BigDecimal) row[3];
            dataMap.put(year + "_" + month + "_" + type, sum);
        }

        List<EmployeeSpendingAnalyticsResponse.SpendingPoint> points = new ArrayList<>();
        for (int[] ym : months) {
            int year  = ym[0];
            int month = ym[1];
            String yy = String.valueOf(year).substring(2);
            String label = "T" + month + "/" + yy;

            BigDecimal expense   = dataMap.getOrDefault(year + "_" + month + "_EXPENSE",   BigDecimal.ZERO);
            BigDecimal reimburse = dataMap.getOrDefault(year + "_" + month + "_REIMBURSE", BigDecimal.ZERO);
            BigDecimal advance   = dataMap.getOrDefault(year + "_" + month + "_ADVANCE",   BigDecimal.ZERO);

            points.add(EmployeeSpendingAnalyticsResponse.SpendingPoint.builder()
                    .label(label)
                    .chiTieu(expense.add(reimburse))
                    .tamUng(advance)
                    .build());
        }

        return EmployeeSpendingAnalyticsResponse.builder()
                .points(points)
                .build();
    }

    // ── Private helpers ──────────────────────────────────────────────

    private List<int[]> resolveMonths(String period) {
        LocalDate now = LocalDate.now();
        int currentYear  = now.getYear();
        int currentMonth = now.getMonthValue();

        if ("ytd".equalsIgnoreCase(period)) {
            List<int[]> list = new ArrayList<>();
            for (int m = 1; m <= currentMonth; m++) list.add(new int[]{currentYear, m});
            return list;
        }
        if ("last6m".equalsIgnoreCase(period)) {
            List<int[]> list = new ArrayList<>();
            for (int i = 5; i >= 0; i--) {
                LocalDate d = now.minusMonths(i);
                list.add(new int[]{d.getYear(), d.getMonthValue()});
            }
            return list;
        }
        if (period != null && period.toLowerCase().startsWith("fy")) {
            int year = Integer.parseInt(period.substring(2));
            int maxMonth = (year == currentYear) ? currentMonth : 12;
            List<int[]> list = new ArrayList<>();
            for (int m = 1; m <= maxMonth; m++) list.add(new int[]{year, m});
            return list;
        }
        // default: last6m
        return resolveMonths("last6m");
    }

    private String buildLabel(int year, int month, String period) {
        boolean isFy = period != null && period.toLowerCase().startsWith("fy");
        if (isFy) {
            return "T" + month;
        }
        // ytd or last6m: "T{m}/{2-digit-year}"
        String yy = String.valueOf(year).substring(2);
        return "T" + month + "/" + yy;
    }
}
