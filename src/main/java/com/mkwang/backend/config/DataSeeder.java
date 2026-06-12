package com.mkwang.backend.config;

import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.accounting.entity.PayrollPeriod;
import com.mkwang.backend.modules.accounting.entity.PayrollStatus;
import com.mkwang.backend.modules.accounting.entity.Payslip;
import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import com.mkwang.backend.modules.accounting.repository.PayrollPeriodRepository;
import com.mkwang.backend.modules.accounting.repository.PayslipRepository;
import com.mkwang.backend.modules.audit.entity.AuditAction;
import com.mkwang.backend.modules.audit.entity.AuditLog;
import com.mkwang.backend.modules.audit.repository.AuditLogRepository;
import com.mkwang.backend.modules.notification.entity.Notification;
import com.mkwang.backend.modules.notification.entity.NotificationType;
import com.mkwang.backend.modules.notification.repository.NotificationRepository;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.organization.repository.DepartmentRepository;
import com.mkwang.backend.modules.project.entity.*;
import com.mkwang.backend.modules.project.repository.*;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.request.repository.AdvanceBalanceRepository;
import com.mkwang.backend.modules.request.repository.RequestRepository;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.entity.UserStatus;
import com.mkwang.backend.modules.user.repository.UserRepository;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.wallet.repository.DepositLogRepository;
import com.mkwang.backend.modules.wallet.repository.WalletRepository;
import com.mkwang.backend.modules.wallet.repository.WithdrawRequestRepository;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DataSeeder — Seeds comprehensive demo data after DataInitializer.
 * Idempotent: skips if request data already exists.
 *
 * Seeding order:
 *   0. Department wallets + project membership + non-employee wallet funding
 *   1. Flow 3: DEPARTMENT_TOPUP (Manager → CFO)
 *   2. Flow 2: PROJECT_TOPUP    (TL → Manager)
 *   3. Flow 1: ADVANCE/EXPENSE/REIMBURSE (Employee → TL → Accountant)
 *   4. Payroll (Accountant)
 *   5. Withdrawal requests
 *   6. Notifications
 *   7. Audit logs
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // Repositories
    private final UserRepository                userRepository;
    private final DepartmentRepository          departmentRepository;
    private final ProjectRepository             projectRepository;
    private final ProjectPhaseRepository        projectPhaseRepository;
    private final ProjectMemberRepository       projectMemberRepository;
    private final PhaseCategoryBudgetRepository phaseCategoryBudgetRepository;
    private final ExpenseCategoryRepository     expenseCategoryRepository;
    private final WalletRepository              walletRepository;
    private final DepositLogRepository          depositLogRepository;
    private final RequestRepository             requestRepository;
    private final AdvanceBalanceRepository      advanceBalanceRepository;
    private final PayrollPeriodRepository       payrollPeriodRepository;
    private final PayslipRepository             payslipRepository;
    private final WithdrawRequestRepository     withdrawRequestRepository;
    private final NotificationRepository        notificationRepository;
    private final AuditLogRepository            auditLogRepository;

    // Services / Utils
    private final WalletService         walletService;
    private final BusinessCodeGenerator codeGen;

    // ── Amount constants ─────────────────────────────────────────────
    private static final BigDecimal M200   = bd("200000000");
    private static final BigDecimal M100   = bd("100000000");
    private static final BigDecimal M80    = bd("80000000");
    private static final BigDecimal M50    = bd("50000000");
    private static final BigDecimal M40    = bd("40000000");
    private static final BigDecimal M30    = bd("30000000");
    private static final BigDecimal M25    = bd("25000000");
    private static final BigDecimal M20    = bd("20000000");
    private static final BigDecimal M18_5  = bd("18500000");
    private static final BigDecimal M18    = bd("18000000");
    private static final BigDecimal M15    = bd("15000000");
    private static final BigDecimal M14    = bd("14000000");
    private static final BigDecimal M10    = bd("10000000");
    private static final BigDecimal M5     = bd("5000000");
    private static final BigDecimal M4     = bd("4000000");
    private static final BigDecimal M3     = bd("3000000");
    private static final BigDecimal M2     = bd("2000000");
    private static final BigDecimal M1_5   = bd("1500000");
    private static final BigDecimal M1_2   = bd("1200000");
    private static final BigDecimal M1     = bd("1000000");
    private static final BigDecimal K800   = bd("800000");
    private static final BigDecimal K500   = bd("500000");
    private static final BigDecimal K300   = bd("300000");

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    // ════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void run(String... args) {
        if (requestRepository.count() > 0) {
            log.info("DataSeeder: data already exists — skipping.");
            return;
        }

        log.info("╔═══════════════════════════════════════════╗");
        log.info("║       🌱  IFMS DataSeeder START           ║");
        log.info("╚═══════════════════════════════════════════╝");

        // Resolve actors
        User admin      = u("admin@ifms.vn");
        User cfo        = u("cfo@ifms.vn");
        User accountant = u("accountant@ifms.vn");
        User managerIT  = u("manager.it@ifms.vn");
        User tlIT       = u("tl.it@ifms.vn");
        User empIT1     = u("emp.it1@ifms.vn");
        User empIT2     = u("emp.it2@ifms.vn");
        User empSales1  = u("emp.sales1@ifms.vn");
        User empFin1    = u("emp.fin1@ifms.vn");

        Department it    = dept("IT");
        Department sales = dept("SALES");

        Project project = projectRepository.findAll().stream()
                .filter(p -> "PRJ-ERP-2026".equals(p.getProjectCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Project PRJ-ERP-2026 not found"));
        ProjectPhase phase = projectPhaseRepository
                .findByProject_IdOrderByCreatedAtAsc(project.getId())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Phase not found"));

        ExpenseCategory catEquip     = cat("Equipment & Software");
        ExpenseCategory catTravel    = cat("Travel & Accommodation");
        ExpenseCategory catMeals     = cat("Meals & Entertainment");
        ExpenseCategory catOutsource = cat("Outsourcing & Services");

        seedModule0(it, sales, project, empFin1, tlIT, managerIT, accountant, cfo, admin);
        seedClosedPhase(project);
        seedLockedUser(empSales1);
        seedSecondProject(managerIT, tlIT, it);
        seedFlow3(managerIT, cfo, it);
        seedFlow2(tlIT, managerIT, project, it);
        seedFlow1(empIT1, empIT2, empSales1, empFin1, tlIT, accountant,
                  project, phase, catEquip, catTravel, catMeals, catOutsource);
        seedPersonalRequests(tlIT, managerIT, accountant, cfo,
                             project, phase, catEquip, catTravel);
        seedCategorySpending(phase, catEquip, catMeals, catTravel);
        seedPayroll(accountant, empIT1, empIT2, empSales1, empFin1, tlIT, managerIT, cfo);
        seedWithdrawals(empIT1, empIT2, empFin1, tlIT, managerIT);
        seedDeposits(empIT1, empIT2, empSales1, empFin1);
        seedNotifications(empIT1, empIT2, empSales1, empFin1,
                          tlIT, managerIT, accountant, cfo, admin);
        seedAuditLogs(admin, accountant, managerIT);

        log.info("╔═══════════════════════════════════════════╗");
        log.info("║   ✅  DataSeeder completed OK             ║");
        log.info("╚═══════════════════════════════════════════╝");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 0 — Department wallets + memberships + initial balances
    // ════════════════════════════════════════════════════════════════

    private void seedModule0(Department it, Department sales,
                             Project project,
                             User empFin1, User tlIT, User managerIT,
                             User accountant, User cfo, User admin) {
        log.info("── [0] Setup: dept wallets, memberships, initial balances");

        // Create DEPARTMENT wallets (needed for Flow 3/2 transfers)
        ensureDeptWallet(it);
        ensureDeptWallet(dept("FIN"));
        ensureDeptWallet(sales);

        // Add emp.fin1 to IT project (FIN dept employee on cross-team assignment)
        if (!projectMemberRepository.existsByProject_IdAndUser_Id(project.getId(), empFin1.getId())) {
            projectMemberRepository.save(ProjectMember.builder()
                    .id(new ProjectMemberId(project.getId(), empFin1.getId()))
                    .project(project).user(empFin1)
                    .projectRole(ProjectRole.MEMBER).position("Finance Analyst")
                    .build());
            log.info("   ✅ emp.fin1 added to IT project");
        }

        // Add manager.it to IT project so they can submit personal EXPENSE/ADVANCE requests
        if (!projectMemberRepository.existsByProject_IdAndUser_Id(project.getId(), managerIT.getId())) {
            projectMemberRepository.save(ProjectMember.builder()
                    .id(new ProjectMemberId(project.getId(), managerIT.getId()))
                    .project(project).user(managerIT)
                    .projectRole(ProjectRole.MEMBER).position("IT Manager")
                    .build());
            log.info("   ✅ manager.it added to IT project");
        }

        // Admin wallet — admin has no payroll section in sidebar; funded directly
        // TL/Manager/Accountant/CFO wallets are funded via T5/2026 payslips in seedPayroll()
        creditUserWallet(admin.getId(), M10, "Initial balance (Admin demo)");
        log.info("   ✅ Admin wallet funded; TL/Manager/Accountant/CFO funded via T5/2026 payroll");
    }

    private void ensureDeptWallet(Department dept) {
        if (!walletRepository.existsByOwnerTypeAndOwnerId(WalletOwnerType.DEPARTMENT, dept.getId())) {
            walletRepository.save(Wallet.builder()
                    .ownerType(WalletOwnerType.DEPARTMENT)
                    .ownerId(dept.getId())
                    .balance(BigDecimal.ZERO)
                    .lockedBalance(BigDecimal.ZERO)
                    .build());
            log.info("   💳 DEPARTMENT wallet created: {}", dept.getCode());
        }
    }

    /** Transfer from COMPANY_FUND to user wallet (PAYSLIP_PAYMENT). */
    private void creditUserWallet(Long userId, BigDecimal amount, String desc) {
        walletService.transfer(
                WalletOwnerType.COMPANY_FUND, 1L,
                WalletOwnerType.USER, userId,
                amount, TransactionType.PAYSLIP_PAYMENT,
                ReferenceType.SYSTEM, 1L, desc);
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 0b — Closed phase (TL projects phases tab demo)
    // ════════════════════════════════════════════════════════════════

    private void seedClosedPhase(Project project) {
        log.info("── [0b] Closed phase for TL projects/[id] demo");

        boolean alreadyHasClosed = projectPhaseRepository
                .findByProject_IdOrderByCreatedAtAsc(project.getId())
                .stream()
                .anyMatch(ph -> ph.getStatus() == PhaseStatus.CLOSED);
        if (alreadyHasClosed) {
            log.info("   ⏭ Closed phase already exists — skipping");
            return;
        }

        projectPhaseRepository.save(ProjectPhase.builder()
                .phaseCode("PH-PREP-00")
                .name("Phase 0 – Chuẩn Bị & Lên Kế Hoạch")
                .project(project)
                .budgetLimit(bd("50000000"))
                .currentSpent(bd("12500000"))
                .status(PhaseStatus.CLOSED)
                .startDate(LocalDate.of(2025, 10, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .build());
        log.info("   ✅ Phase 0 CLOSED added (50M budget, 12.5M spent)");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 0c — Locked user (admin/users page demo)
    // ════════════════════════════════════════════════════════════════

    private void seedLockedUser(User empSales1) {
        log.info("── [0c] Lock demo user for admin/users LOCKED badge");

        if (empSales1.getStatus() == UserStatus.LOCKED) {
            log.info("   ⏭ emp.sales1 already LOCKED — skipping");
            return;
        }
        empSales1.setStatus(UserStatus.LOCKED);
        userRepository.save(empSales1);
        log.info("   ✅ emp.sales1 LOCKED — admin/users page shows LOCKED badge + unlock button");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 0d — Second project (CLOSED) for manager/TL projects list
    // ════════════════════════════════════════════════════════════════

    private void seedSecondProject(User managerIT, User tlIT, Department it) {
        log.info("── [0d] Second project PRJ-MOBILE-2025 (CLOSED)");

        boolean exists = projectRepository.findAll().stream()
                .anyMatch(p -> "PRJ-MOBILE-2025".equals(p.getProjectCode()));
        if (exists) {
            log.info("   ⏭ PRJ-MOBILE-2025 already exists — skipping");
            return;
        }

        Project p2 = projectRepository.save(Project.builder()
                .projectCode("PRJ-MOBILE-2025")
                .name("Ứng Dụng Mobile Nhân Sự")
                .description("Phát triển ứng dụng mobile quản lý nhân sự nội bộ cho toàn công ty. Dự án đã hoàn thành và đóng cửa.")
                .department(it)
                .manager(managerIT)
                .totalBudget(bd("100000000"))
                .availableBudget(BigDecimal.ZERO)
                .totalSpent(bd("100000000"))
                .status(ProjectStatus.CLOSED)
                .build());

        // TL as LEADER member
        projectMemberRepository.save(ProjectMember.builder()
                .id(new ProjectMemberId(p2.getId(), tlIT.getId()))
                .project(p2).user(tlIT)
                .projectRole(ProjectRole.LEADER).position("Technical Lead")
                .build());

        // One CLOSED phase representing the completed work
        projectPhaseRepository.save(ProjectPhase.builder()
                .phaseCode("PH-MOB-01")
                .name("Phase 1 – Phát Triển & Triển Khai")
                .project(p2)
                .budgetLimit(bd("100000000"))
                .currentSpent(bd("95000000"))
                .status(PhaseStatus.CLOSED)
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 9, 30))
                .build());

        log.info("   ✅ PRJ-MOBILE-2025 CLOSED — manager/TL projects page now shows 2 projects");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 1 — Flow 3: DEPARTMENT_TOPUP
    // ════════════════════════════════════════════════════════════════

    private void seedFlow3(User managerIT, User cfo, Department it) {
        log.info("── [1] Flow 3 DEPARTMENT_TOPUP");

        // 1. PAID — CFO approved, funds transferred
        Request r1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                managerIT, null, null, null,
                RequestType.DEPARTMENT_TOPUP, M200, M200, RequestStatus.PAID,
                "Nạp ngân sách quý 2/2026 cho Phòng Công Nghệ");
        addHistory(r1, cfo, RequestAction.APPROVE, RequestStatus.APPROVED_BY_CFO,
                "Duyệt đúng kế hoạch ngân sách Q2");
        addHistory(r1, cfo, RequestAction.PAYOUT, RequestStatus.PAID,
                "Auto-paid on CFO approval");
        r1.setPaidAt(LocalDateTime.now().minusDays(29));
        requestRepository.save(r1);
        walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                WalletOwnerType.DEPARTMENT, it.getId(), M200,
                TransactionType.DEPT_QUOTA_ALLOCATION, ReferenceType.REQUEST, r1.getId(),
                "DEPT_TOPUP paid — " + r1.getRequestCode());
        log.info("   ✅ DEPT_TOPUP PAID 200M → IT dept");

        // 2. APPROVED_BY_CFO — pending auto-pay
        Request r2 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                managerIT, null, null, null,
                RequestType.DEPARTMENT_TOPUP, M100, M100, RequestStatus.APPROVED_BY_CFO,
                "Bổ sung ngân sách tháng 7/2026 cho Phòng Công Nghệ");
        addHistory(r2, cfo, RequestAction.APPROVE, RequestStatus.APPROVED_BY_CFO,
                "Chấp thuận bổ sung theo nhu cầu thực tế");
        requestRepository.save(r2);
        log.info("   ✅ DEPT_TOPUP APPROVED_BY_CFO 100M");

        // 3. PENDING — waiting for CFO
        Request r3 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                managerIT, null, null, null,
                RequestType.DEPARTMENT_TOPUP, M50, null, RequestStatus.PENDING,
                "Dự phòng ngân sách tháng 8/2026 cho Phòng Công Nghệ");
        requestRepository.save(r3);
        log.info("   ✅ DEPT_TOPUP PENDING 50M (in CFO queue)");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 2 — Flow 2: PROJECT_TOPUP
    // ════════════════════════════════════════════════════════════════

    private void seedFlow2(User tlIT, User managerIT, Project project, Department it) {
        log.info("── [2] Flow 2 PROJECT_TOPUP");

        // 1. PAID — Manager approved, funds transferred
        Request r1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                tlIT, project, null, null,
                RequestType.PROJECT_TOPUP, M80, M80, RequestStatus.PAID,
                "Nạp quỹ dự án ERP giai đoạn 1 — mua sắm thiết bị");
        addHistory(r1, managerIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_MANAGER,
                "Duyệt theo kế hoạch dự án đã được phê duyệt");
        addHistory(r1, managerIT, RequestAction.PAYOUT, RequestStatus.PAID,
                "Auto-paid on manager approval");
        r1.setPaidAt(LocalDateTime.now().minusDays(19));
        requestRepository.save(r1);
        walletService.transfer(WalletOwnerType.DEPARTMENT, it.getId(),
                WalletOwnerType.PROJECT, project.getId(), M80,
                TransactionType.PROJECT_QUOTA_ALLOCATION, ReferenceType.REQUEST, r1.getId(),
                "PROJECT_TOPUP paid — " + r1.getRequestCode());
        log.info("   ✅ PROJECT_TOPUP PAID 80M → project");

        // 2. APPROVED_BY_MANAGER — pending auto-pay
        Request r2 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                tlIT, project, null, null,
                RequestType.PROJECT_TOPUP, M40, M40, RequestStatus.APPROVED_BY_MANAGER,
                "Nạp bổ sung cho giai đoạn phát triển tính năng mới");
        addHistory(r2, managerIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_MANAGER,
                "Chấp thuận theo tiến độ dự án");
        requestRepository.save(r2);
        log.info("   ✅ PROJECT_TOPUP APPROVED_BY_MANAGER 40M");

        // 3. PENDING — waiting for Manager
        Request r3 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                tlIT, project, null, null,
                RequestType.PROJECT_TOPUP, M20, null, RequestStatus.PENDING,
                "Dự phòng kinh phí vận hành dự án tháng 7/2026");
        requestRepository.save(r3);
        log.info("   ✅ PROJECT_TOPUP PENDING 20M (in Manager queue)");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 3 — Flow 1: ADVANCE / EXPENSE / REIMBURSE
    // ════════════════════════════════════════════════════════════════

    private void seedFlow1(User empIT1, User empIT2, User empSales1, User empFin1,
                           User tlIT, User accountant, Project project, ProjectPhase phase,
                           ExpenseCategory catEquip, ExpenseCategory catTravel,
                           ExpenseCategory catMeals, ExpenseCategory catOutsource) {
        log.info("── [3] Flow 1 ADVANCE/EXPENSE/REIMBURSE");

        // ── emp.it1 (7 requests, all statuses) ──────────────────────

        // [1] ADVANCE PAID 3M → creates advance_balance (will be linked by REIMBURSE)
        Request adv1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catEquip,
                RequestType.ADVANCE, M3, M3, RequestStatus.PAID,
                "Tạm ứng mua thiết bị phát triển — laptop cho team");
        addHistory(adv1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Duyệt — đúng hạn mục thiết bị");
        addHistory(adv1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân sau khi xác minh chứng từ");
        adv1.setPaidAt(LocalDateTime.now().minusDays(24));
        requestRepository.save(adv1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, empIT1.getId(), M3,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, adv1.getId(),
                "ADVANCE paid — " + adv1.getRequestCode());
        AdvanceBalance ab1 = AdvanceBalance.builder()
                .user(empIT1).advanceRequest(adv1)
                .originalAmount(M3).remainingAmount(M3)
                .build();
        advanceBalanceRepository.save(ab1);
        log.info("   ✅ emp.it1 ADVANCE PAID 3M");

        // [2] EXPENSE PAID 500K
        Request exp1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catMeals,
                RequestType.EXPENSE, K500, K500, RequestStatus.PAID,
                "Chi phí ăn uống team building sprint review");
        addHistory(exp1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "OK — phù hợp hạng mục");
        addHistory(exp1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Đã giải ngân sau khi nhận hóa đơn");
        exp1.setPaidAt(LocalDateTime.now().minusDays(14));
        requestRepository.save(exp1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, empIT1.getId(), K500,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, exp1.getId(),
                "EXPENSE paid — " + exp1.getRequestCode());
        log.info("   ✅ emp.it1 EXPENSE PAID 500K");

        // [3] REIMBURSE APPROVED_BY_TL 2M — in Accountant disbursements queue
        Request reimb1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catEquip,
                RequestType.REIMBURSE, M2, M2, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Hoàn ứng khoản tạm ứng — nộp hóa đơn laptop đã mua");
        reimb1.setAdvanceBalance(ab1);
        addHistory(reimb1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Chứng từ hợp lệ — chuyển kế toán xử lý");
        requestRepository.save(reimb1);
        log.info("   ✅ emp.it1 REIMBURSE APPROVED_BY_TL 2M (in disburse queue)");

        // [4] ADVANCE PENDING 1.5M — in TL approvals queue
        Request adv2 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catTravel,
                RequestType.ADVANCE, M1_5, null, RequestStatus.PENDING,
                "Tạm ứng công tác Hà Nội — họp khách hàng Q3");
        requestRepository.save(adv2);
        log.info("   ✅ emp.it1 ADVANCE PENDING 1.5M (in TL queue)");

        // [5] EXPENSE REJECTED 2M
        Request exp2 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catOutsource,
                RequestType.EXPENSE, M2, null, RequestStatus.REJECTED,
                "Chi phí thuê tư vấn bên ngoài — khảo sát UX");
        exp2.setRejectReason("Chưa có quyết định chính thức thuê tư vấn. Cần xin phê duyệt từ Manager trước.");
        addHistory(exp2, tlIT, RequestAction.REJECT, RequestStatus.REJECTED,
                "Chưa có quyết định chính thức. Cần Manager duyệt trước.");
        requestRepository.save(exp2);
        log.info("   ✅ emp.it1 EXPENSE REJECTED 2M");

        // [6] EXPENSE CANCELLED 800K
        Request exp3 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catMeals,
                RequestType.EXPENSE, K800, null, RequestStatus.CANCELLED,
                "Chi phí cà phê làm việc ngoài văn phòng");
        addHistory(exp3, empIT1, RequestAction.CANCEL, RequestStatus.CANCELLED,
                "Tự hủy — không phù hợp chính sách");
        requestRepository.save(exp3);
        log.info("   ✅ emp.it1 EXPENSE CANCELLED 800K");

        // [7] ADVANCE PAID 4M — 2nd advance balance (OUTSTANDING, for REIMBURSE demo)
        Request adv3 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT1, project, phase, catEquip,
                RequestType.ADVANCE, M4, M4, RequestStatus.PAID,
                "Tạm ứng mua màn hình 4K và thiết bị ngoại vi");
        addHistory(adv3, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Duyệt theo kế hoạch mua sắm Q2");
        addHistory(adv3, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân sau khi nhận đơn mua hàng");
        adv3.setPaidAt(LocalDateTime.now().minusDays(7));
        requestRepository.save(adv3);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, empIT1.getId(), M4,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, adv3.getId(),
                "ADVANCE paid — " + adv3.getRequestCode());
        advanceBalanceRepository.save(AdvanceBalance.builder()
                .user(empIT1).advanceRequest(adv3)
                .originalAmount(M4).remainingAmount(M4)
                .build());
        log.info("   ✅ emp.it1 ADVANCE PAID 4M (2nd advance_balance OUTSTANDING)");

        // ── emp.it2 (3 requests) ────────────────────────────────────

        // [1] EXPENSE PAID 5M
        Request it2e1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT2, project, phase, catEquip,
                RequestType.EXPENSE, M5, M5, RequestStatus.PAID,
                "Chi phí bản quyền phần mềm phát triển — JetBrains All Products");
        addHistory(it2e1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Phần mềm cần thiết — duyệt");
        addHistory(it2e1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân sau khi xác nhận invoice");
        it2e1.setPaidAt(LocalDateTime.now().minusDays(19));
        requestRepository.save(it2e1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, empIT2.getId(), M5,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, it2e1.getId(),
                "EXPENSE paid — " + it2e1.getRequestCode());
        log.info("   ✅ emp.it2 EXPENSE PAID 5M");

        // [2] ADVANCE APPROVED_BY_TL 2M — in Accountant disbursements queue
        Request it2a1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT2, project, phase, catTravel,
                RequestType.ADVANCE, M2, M2, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Tạm ứng tham dự hội thảo công nghệ Hà Nội");
        addHistory(it2a1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Phù hợp kế hoạch đào tạo năm 2026");
        requestRepository.save(it2a1);
        log.info("   ✅ emp.it2 ADVANCE APPROVED_BY_TL 2M (in disburse queue)");

        // [3] EXPENSE PENDING 1.2M — in TL queue
        Request it2e2 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                empIT2, project, phase, catMeals,
                RequestType.EXPENSE, M1_2, null, RequestStatus.PENDING,
                "Chi phí ăn trưa làm việc ngoài giờ — sprint cuối Q2");
        requestRepository.save(it2e2);
        log.info("   ✅ emp.it2 EXPENSE PENDING 1.2M (in TL queue)");

        // ── emp.sales1 (2 requests) ─────────────────────────────────

        // [1] EXPENSE PAID 1.5M
        Request se1 = req(codeGen.generate(BusinessCodeType.REQUEST, "SALES"),
                empSales1, project, phase, catMeals,
                RequestType.EXPENSE, M1_5, M1_5, RequestStatus.PAID,
                "Chi phí tiếp khách dịp ký kết hợp đồng Q2");
        addHistory(se1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "OK — có biên bản tiếp khách đính kèm");
        addHistory(se1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân sau khi kiểm tra chứng từ");
        se1.setPaidAt(LocalDateTime.now().minusDays(17));
        requestRepository.save(se1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, empSales1.getId(), M1_5,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, se1.getId(),
                "EXPENSE paid — " + se1.getRequestCode());
        log.info("   ✅ emp.sales1 EXPENSE PAID 1.5M");

        // [2] ADVANCE PENDING 3M — in TL queue
        Request sa1 = req(codeGen.generate(BusinessCodeType.REQUEST, "SALES"),
                empSales1, project, phase, catEquip,
                RequestType.ADVANCE, M3, null, RequestStatus.PENDING,
                "Tạm ứng mua thiết bị demo sản phẩm cho khách hàng");
        requestRepository.save(sa1);
        log.info("   ✅ emp.sales1 ADVANCE PENDING 3M (in TL queue)");

        // ── emp.fin1 (2 requests) ────────────────────────────────────

        // [1] EXPENSE PENDING 2M — in TL queue
        Request fe1 = req(codeGen.generate(BusinessCodeType.REQUEST, "FIN"),
                empFin1, project, phase, catTravel,
                RequestType.EXPENSE, M2, null, RequestStatus.PENDING,
                "Chi phí tham dự hội thảo kế toán tài chính doanh nghiệp");
        requestRepository.save(fe1);
        log.info("   ✅ emp.fin1 EXPENSE PENDING 2M (in TL queue)");

        // [2] ADVANCE PAID 1M
        Request fa1 = req(codeGen.generate(BusinessCodeType.REQUEST, "FIN"),
                empFin1, project, phase, catMeals,
                RequestType.ADVANCE, M1, M1, RequestStatus.PAID,
                "Tạm ứng chi phí đi lại tháng 6/2026");
        addHistory(fa1, tlIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Duyệt theo quy định công tác phí");
        addHistory(fa1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân đúng hạn");
        fa1.setPaidAt(LocalDateTime.now().minusDays(11));
        requestRepository.save(fa1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, empFin1.getId(), M1,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, fa1.getId(),
                "ADVANCE paid — " + fa1.getRequestCode());
        advanceBalanceRepository.save(AdvanceBalance.builder()
                .user(empFin1).advanceRequest(fa1)
                .originalAmount(M1).remainingAmount(M1)
                .build());
        log.info("   ✅ emp.fin1 ADVANCE PAID 1M");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 3b — Personal requests for TL and Manager
    // ════════════════════════════════════════════════════════════════

    private void seedPersonalRequests(User tlIT, User managerIT, User accountant, User cfo,
                                      Project project, ProjectPhase phase,
                                      ExpenseCategory catEquip, ExpenseCategory catTravel) {
        log.info("── [3b] Personal requests — TL and Manager");

        // ── tl.it: 1 PAID EXPENSE + 1 PENDING ADVANCE ───────────────
        // TL's own requests: manager.it acts as approver (role hierarchy)
        Request tlExp1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                tlIT, project, phase, catEquip,
                RequestType.EXPENSE, M1_5, M1_5, RequestStatus.PAID,
                "Chi phí nâng cấp thiết bị cá nhân phục vụ dự án ERP");
        addHistory(tlExp1, managerIT, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Duyệt — thiết bị phục vụ trực tiếp dự án");
        addHistory(tlExp1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân sau khi nhận hóa đơn từ TL");
        tlExp1.setPaidAt(LocalDateTime.now().minusDays(8));
        requestRepository.save(tlExp1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, tlIT.getId(), M1_5,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, tlExp1.getId(),
                "EXPENSE paid — " + tlExp1.getRequestCode());
        log.info("   ✅ tl.it EXPENSE PAID 1.5M");

        Request tlAdv1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                tlIT, project, phase, catTravel,
                RequestType.ADVANCE, M2, null, RequestStatus.PENDING,
                "Tạm ứng công tác khảo sát hệ thống tại chi nhánh Hà Nội");
        requestRepository.save(tlAdv1);
        log.info("   ✅ tl.it ADVANCE PENDING 2M");

        // ── manager.it: 1 PAID EXPENSE + 1 PENDING ADVANCE ──────────
        // Manager's own requests: cfo acts as approver
        Request mgExp1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                managerIT, project, phase, catTravel,
                RequestType.EXPENSE, M2, M2, RequestStatus.PAID,
                "Chi phí tham dự hội nghị quản lý công nghệ TP.HCM 2026");
        addHistory(mgExp1, cfo, RequestAction.APPROVE, RequestStatus.APPROVED_BY_TEAM_LEADER,
                "Duyệt — phù hợp kế hoạch đào tạo lãnh đạo");
        addHistory(mgExp1, accountant, RequestAction.PAYOUT, RequestStatus.PAID,
                "Giải ngân sau khi xác nhận chứng từ hội nghị");
        mgExp1.setPaidAt(LocalDateTime.now().minusDays(6));
        requestRepository.save(mgExp1);
        walletService.transfer(WalletOwnerType.PROJECT, project.getId(),
                WalletOwnerType.USER, managerIT.getId(), M2,
                TransactionType.REQUEST_PAYMENT, ReferenceType.REQUEST, mgExp1.getId(),
                "EXPENSE paid — " + mgExp1.getRequestCode());
        log.info("   ✅ manager.it EXPENSE PAID 2M");

        Request mgAdv1 = req(codeGen.generate(BusinessCodeType.REQUEST, "IT"),
                managerIT, project, phase, catEquip,
                RequestType.ADVANCE, M3, null, RequestStatus.PENDING,
                "Tạm ứng trang thiết bị phòng họp mới cho team IT");
        requestRepository.save(mgAdv1);
        log.info("   ✅ manager.it ADVANCE PENDING 3M");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 4 — Payroll
    // ════════════════════════════════════════════════════════════════

    private void seedPayroll(User accountant,
                             User empIT1, User empIT2, User empSales1, User empFin1,
                             User tlIT, User managerIT, User cfo) {
        log.info("── [4] Payroll");

        // Period 1 — T5/2026 — COMPLETED
        if (!payrollPeriodRepository.existsByMonthAndYear(5, 2026)) {
            PayrollPeriod p1 = payrollPeriodRepository.save(PayrollPeriod.builder()
                    .periodCode("PR-2026-05")
                    .name("Kỳ lương tháng 5/2026")
                    .month(5).year(2026)
                    .startDate(LocalDate.of(2026, 5, 1))
                    .endDate(LocalDate.of(2026, 5, 31))
                    .status(PayrollStatus.COMPLETED)
                    .nettingApplied(true)
                    .build());

            // emp.it1: 20M gross, 1.5M advance deduct, net 18.5M
            Payslip ps1 = payslipRepository.save(payslip(p1, empIT1, "MK004",
                    bd("20000000"), BigDecimal.ZERO, bd("500000"),
                    BigDecimal.ZERO, M1_5, M18_5, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, empIT1.getId(), M18_5,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps1.getId(),
                    "Payslip " + ps1.getPayslipCode());

            // emp.it2: 18M net
            Payslip ps2 = payslipRepository.save(payslip(p1, empIT2, "MK005",
                    M18, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M18, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, empIT2.getId(), M18,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps2.getId(),
                    "Payslip " + ps2.getPayslipCode());

            // emp.sales1: 15M net
            Payslip ps3 = payslipRepository.save(payslip(p1, empSales1, "MK006",
                    M15, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M15, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, empSales1.getId(), M15,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps3.getId(),
                    "Payslip " + ps3.getPayslipCode());

            // emp.fin1: 14M net
            Payslip ps4 = payslipRepository.save(payslip(p1, empFin1, "MK007",
                    M14, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M14, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, empFin1.getId(), M14,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps4.getId(),
                    "Payslip " + ps4.getPayslipCode());

            // tl.it: 25M net (no deductions for demo clarity)
            Payslip ps5 = payslipRepository.save(payslip(p1, tlIT, "MK008",
                    M25, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M25, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, tlIT.getId(), M25,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps5.getId(),
                    "Payslip " + ps5.getPayslipCode());

            // manager.it: 30M net
            Payslip ps6 = payslipRepository.save(payslip(p1, managerIT, "MK002",
                    M30, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M30, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, managerIT.getId(), M30,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps6.getId(),
                    "Payslip " + ps6.getPayslipCode());

            // accountant: 20M net
            Payslip ps7 = payslipRepository.save(payslip(p1, accountant, "MK001",
                    M20, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M20, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, accountant.getId(), M20,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps7.getId(),
                    "Payslip " + ps7.getPayslipCode());

            // cfo: 50M net
            Payslip ps8 = payslipRepository.save(payslip(p1, cfo, "MK010",
                    M50, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, M50, 5, 2026));
            walletService.transfer(WalletOwnerType.COMPANY_FUND, 1L,
                    WalletOwnerType.USER, cfo.getId(), M50,
                    TransactionType.PAYSLIP_PAYMENT, ReferenceType.PAYSLIP, ps8.getId(),
                    "Payslip " + ps8.getPayslipCode());

            log.info("   ✅ T5/2026 COMPLETED — 8 payslips PAID (Employees×4 + TL + Manager + Accountant + CFO)");
        }

        // Period 2 — T6/2026 — DRAFT
        if (!payrollPeriodRepository.existsByMonthAndYear(6, 2026)) {
            payrollPeriodRepository.save(PayrollPeriod.builder()
                    .periodCode("PR-2026-06")
                    .name("Kỳ lương tháng 6/2026")
                    .month(6).year(2026)
                    .startDate(LocalDate.of(2026, 6, 1))
                    .endDate(LocalDate.of(2026, 6, 30))
                    .status(PayrollStatus.DRAFT)
                    .nettingApplied(false)
                    .build());
            log.info("   ✅ T6/2026 DRAFT created");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 5 — Withdrawal requests
    // ════════════════════════════════════════════════════════════════

    private void seedWithdrawals(User empIT1, User empIT2, User empFin1,
                                 User tlIT, User managerIT) {
        log.info("── [5] Withdrawal requests");

        // emp.it1 — COMPLETED 5M
        WithdrawRequest w1 = withdrawRequestRepository.save(WithdrawRequest.builder()
                .withdrawCode(codeGen.generate(BusinessCodeType.WITHDRAWAL))
                .userId(empIT1.getId()).amount(M5)
                .creditAccount("0011004000005").creditAccountName("DO QUOC BAO")
                .creditBankCode("VCB").creditBankName("Vietcombank")
                .userNote("Rút tiền sinh hoạt phí tháng 6/2026")
                .status(WithdrawStatus.COMPLETED)
                .bankTransactionId("VCB20260601000001")
                .executedBy(1L).executedAt(LocalDateTime.now().minusDays(5))
                .build());
        walletService.lockFunds(WalletOwnerType.USER, empIT1.getId(), M5);
        walletService.withdraw(empIT1.getId(), M5, "VCB20260601000001", w1.getId());
        log.info("   ✅ emp.it1 COMPLETED 5M");

        // tl.it — PENDING 10M (funds locked, awaiting accountant execution)
        withdrawRequestRepository.save(WithdrawRequest.builder()
                .withdrawCode(codeGen.generate(BusinessCodeType.WITHDRAWAL))
                .userId(tlIT.getId()).amount(M10)
                .creditAccount("0011004000009").creditAccountName("HOANG MINH TUAN")
                .creditBankCode("VCB").creditBankName("Vietcombank")
                .userNote("Rút tiền dịp lễ")
                .status(WithdrawStatus.PENDING)
                .build());
        walletService.lockFunds(WalletOwnerType.USER, tlIT.getId(), M10);
        log.info("   ✅ tl.it PENDING 10M (10M locked)");

        // manager.it — COMPLETED 15M
        WithdrawRequest w3 = withdrawRequestRepository.save(WithdrawRequest.builder()
                .withdrawCode(codeGen.generate(BusinessCodeType.WITHDRAWAL))
                .userId(managerIT.getId()).amount(M15)
                .creditAccount("0011004000003").creditAccountName("TRAN THI BICH")
                .creditBankCode("TCB").creditBankName("Techcombank")
                .userNote("Rút tiền")
                .status(WithdrawStatus.COMPLETED)
                .bankTransactionId("TCB20260520000001")
                .executedBy(1L).executedAt(LocalDateTime.now().minusDays(10))
                .build());
        walletService.lockFunds(WalletOwnerType.USER, managerIT.getId(), M15);
        walletService.withdraw(managerIT.getId(), M15, "TCB20260520000001", w3.getId());
        log.info("   ✅ manager.it COMPLETED 15M");

        // emp.it2 — REJECTED 2M (accountant từ chối)
        withdrawRequestRepository.save(WithdrawRequest.builder()
                .withdrawCode(codeGen.generate(BusinessCodeType.WITHDRAWAL))
                .userId(empIT2.getId()).amount(M2)
                .creditAccount("0011004000006").creditAccountName("VU THI LAN")
                .creditBankCode("MBB").creditBankName("MBBank")
                .userNote("Rút tiền mua sắm cá nhân")
                .status(WithdrawStatus.REJECTED)
                .accountantNote("Tài khoản đích không khớp với hồ sơ nhân viên. Vui lòng cập nhật thông tin ngân hàng.")
                .executedBy(1L).executedAt(LocalDateTime.now().minusDays(2))
                .build());
        log.info("   ✅ emp.it2 REJECTED 2M");

        // emp.fin1 — CANCELLED 1M (user tự hủy)
        withdrawRequestRepository.save(WithdrawRequest.builder()
                .withdrawCode(codeGen.generate(BusinessCodeType.WITHDRAWAL))
                .userId(empFin1.getId()).amount(M1)
                .creditAccount("0011004000008").creditAccountName("NGUYEN THI MINH")
                .creditBankCode("VCB").creditBankName("Vietcombank")
                .userNote("Rút tiền cá nhân")
                .status(WithdrawStatus.CANCELLED)
                .build());
        log.info("   ✅ emp.fin1 CANCELLED 1M");
    }

    // ═════════════════════════════════════════════════════��══════════
    //  MODULE 5b — Update PhaseCategoryBudget currentSpent
    // ════════════════════════════════════════════════════════════════

    private void seedCategorySpending(ProjectPhase phase,
                                      ExpenseCategory catEquip,
                                      ExpenseCategory catMeals,
                                      ExpenseCategory catTravel) {
        log.info("── [5b] Category spending (update currentSpent)");

        // catEquip: adv1(3M) + adv3(4M) + it2e1(5M) + tlExp1(1.5M) = 13.5M
        PhaseCategoryBudgetId idEquip = new PhaseCategoryBudgetId(phase.getId(), catEquip.getId());
        phaseCategoryBudgetRepository.findById(idEquip).ifPresent(b -> {
            b.addSpent(bd("13500000"));
            phaseCategoryBudgetRepository.save(b);
        });

        // catMeals: exp1(500K) + se1(1.5M) + fa1(1M) = 3M
        PhaseCategoryBudgetId idMeals = new PhaseCategoryBudgetId(phase.getId(), catMeals.getId());
        phaseCategoryBudgetRepository.findById(idMeals).ifPresent(b -> {
            b.addSpent(bd("3000000"));
            phaseCategoryBudgetRepository.save(b);
        });

        // catTravel: mgExp1(2M) = 2M
        PhaseCategoryBudgetId idTravel = new PhaseCategoryBudgetId(phase.getId(), catTravel.getId());
        phaseCategoryBudgetRepository.findById(idTravel).ifPresent(b -> {
            b.addSpent(bd("2000000"));
            phaseCategoryBudgetRepository.save(b);
        });

        // ProjectPhase.currentSpent — backend service never updates this field (only PhaseCategoryBudget
        // is updated on disbursement). Set it directly so TL approvals budget warning has correct data.
        // Total PAID on Phase 1: 13.5M (equip) + 3M (meals) + 2M (travel) = 18.5M
        phase.setCurrentSpent(bd("18500000"));
        projectPhaseRepository.save(phase);

        log.info("   ✅ catEquip +13.5M, catMeals +3M, catTravel +2M | phase.currentSpent = 18.5M");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 5c — Deposit history (VNPay)
    // ════════════════════════════════════════════════════════════════

    private void seedDeposits(User empIT1, User empIT2, User empSales1, User empFin1) {
        log.info("── [5c] Deposit history");

        // emp.it1 — COMPLETED 2M (credited to wallet)
        DepositLog d1 = depositLogRepository.save(DepositLog.builder()
                .depositCode(codeGen.generate(BusinessCodeType.DEPOSIT))
                .userId(empIT1.getId())
                .amount(M2)
                .status(DepositStatus.COMPLETED)
                .vnpTransactionNo("VNP20260520000001")
                .vnpResponseCode("00")
                .paidAt(LocalDateTime.now().minusDays(12))
                .build());
        walletService.deposit(empIT1.getId(), M2, "VNP20260520000001", d1.getId());
        log.info("   ✅ emp.it1 deposit COMPLETED 2M");

        // emp.it1 — PENDING 500K (awaiting VNPay callback)
        depositLogRepository.save(DepositLog.builder()
                .depositCode(codeGen.generate(BusinessCodeType.DEPOSIT))
                .userId(empIT1.getId())
                .amount(K500)
                .status(DepositStatus.PENDING)
                .build());
        log.info("   ✅ emp.it1 deposit PENDING 500K");

        // emp.it2 — COMPLETED 1M
        DepositLog d3 = depositLogRepository.save(DepositLog.builder()
                .depositCode(codeGen.generate(BusinessCodeType.DEPOSIT))
                .userId(empIT2.getId())
                .amount(M1)
                .status(DepositStatus.COMPLETED)
                .vnpTransactionNo("VNP20260518000001")
                .vnpResponseCode("00")
                .paidAt(LocalDateTime.now().minusDays(14))
                .build());
        walletService.deposit(empIT2.getId(), M1, "VNP20260518000001", d3.getId());
        log.info("   ✅ emp.it2 deposit COMPLETED 1M");

        // emp.sales1 — COMPLETED 800K
        DepositLog d4 = depositLogRepository.save(DepositLog.builder()
                .depositCode(codeGen.generate(BusinessCodeType.DEPOSIT))
                .userId(empSales1.getId())
                .amount(K800)
                .status(DepositStatus.COMPLETED)
                .vnpTransactionNo("VNP20260515000001")
                .vnpResponseCode("00")
                .paidAt(LocalDateTime.now().minusDays(17))
                .build());
        walletService.deposit(empSales1.getId(), K800, "VNP20260515000001", d4.getId());
        log.info("   ✅ emp.sales1 deposit COMPLETED 800K");

        // emp.fin1 — FAILED 300K (payment cancelled by user)
        depositLogRepository.save(DepositLog.builder()
                .depositCode(codeGen.generate(BusinessCodeType.DEPOSIT))
                .userId(empFin1.getId())
                .amount(K300)
                .status(DepositStatus.FAILED)
                .vnpResponseCode("24")
                .build());
        log.info("   ✅ emp.fin1 deposit FAILED 300K");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 6 — Notifications
    // ════════════════════════════════════════════════════════════════

    private void seedNotifications(User empIT1, User empIT2, User empSales1, User empFin1,
                                   User tlIT, User managerIT, User accountant,
                                   User cfo, User admin) {
        log.info("── [6] Notifications");

        // emp.it1
        notif(empIT1, "Yêu cầu của bạn đã được duyệt",
                "Yêu cầu tạm ứng đã được Team Leader phê duyệt và chuyển kế toán xử lý.",
                NotificationType.REQUEST_APPROVED_BY_TL, true);
        notif(empIT1, "Giải ngân thành công",
                "Khoản tạm ứng 3.000.000 ₫ đã được giải ngân vào ví của bạn.",
                NotificationType.REQUEST_PAID, true);
        notif(empIT1, "Phiếu lương tháng 5/2026 đã sẵn sàng",
                "Phiếu lương kỳ tháng 5/2026 với số tiền 18.500.000 ₫ đã được cập nhật.",
                NotificationType.SALARY_PAID, false);

        // emp.it2
        notif(empIT2, "Yêu cầu chi phí đã được phê duyệt và giải ngân",
                "Yêu cầu chi phí 5.000.000 ₫ đã được phê duyệt và giải ngân thành công.",
                NotificationType.REQUEST_PAID, true);
        notif(empIT2, "Phiếu lương tháng 5/2026 đã sẵn sàng",
                "Phiếu lương kỳ tháng 5/2026 với số tiền 18.000.000 ₫ đã được cập nhật.",
                NotificationType.SALARY_PAID, false);

        // emp.sales1
        notif(empSales1, "Yêu cầu chi phí tiếp khách đã giải ngân",
                "Yêu cầu chi phí tiếp khách 1.500.000 ₫ đã được giải ngân thành công.",
                NotificationType.REQUEST_PAID, true);
        notif(empSales1, "Phiếu lương tháng 5/2026 đã sẵn sàng",
                "Phiếu lương kỳ tháng 5/2026 với số tiền 15.000.000 ₫ đã được cập nhật.",
                NotificationType.SALARY_PAID, false);

        // emp.fin1
        notif(empFin1, "Yêu cầu của bạn đã được giải ngân",
                "Khoản tạm ứng 1.000.000 ₫ đã được chuyển vào ví.",
                NotificationType.REQUEST_PAID, true);
        notif(empFin1, "Phiếu lương tháng 5/2026 đã sẵn sàng",
                "Phiếu lương kỳ tháng 5/2026 với số tiền 14.000.000 ₫ đã được cập nhật.",
                NotificationType.SALARY_PAID, false);

        // tl.it
        notif(tlIT, "Có 3 yêu cầu mới chờ bạn duyệt",
                "Nhân viên trong nhóm đã gửi 3 yêu cầu ADVANCE/EXPENSE mới. Vui lòng xem xét.",
                NotificationType.SYSTEM, false);
        notif(tlIT, "Yêu cầu nạp quỹ dự án đã được duyệt",
                "Yêu cầu PROJECT_TOPUP 80.000.000 ₫ đã được Manager phê duyệt và chuyển vào quỹ dự án.",
                NotificationType.PROJECT_TOPUP_APPROVED, true);
        notif(tlIT, "Yêu cầu PROJECT_TOPUP đang chờ Manager xem xét",
                "Yêu cầu nạp quỹ dự án 20.000.000 ₫ của bạn đã được gửi đến Manager.",
                NotificationType.SYSTEM, false);

        // manager.it
        notif(managerIT, "Có 1 yêu cầu PROJECT_TOPUP mới",
                "Team Leader đã gửi yêu cầu nạp quỹ dự án 20.000.000 ₫ chờ bạn phê duyệt.",
                NotificationType.SYSTEM, false);
        notif(managerIT, "Yêu cầu ngân sách phòng ban đã được CFO duyệt",
                "Yêu cầu DEPARTMENT_TOPUP 200.000.000 ₫ đã được CFO phê duyệt và chuyển vào quỹ phòng ban.",
                NotificationType.DEPT_TOPUP_APPROVED, true);
        notif(managerIT, "Yêu cầu ngân sách đang chờ CFO xem xét",
                "Yêu cầu cấp ngân sách 50.000.000 ₫ của bạn đang trong hàng đợi phê duyệt của CFO.",
                NotificationType.SYSTEM, false);

        // accountant
        notif(accountant, "Có 2 yêu cầu đang chờ giải ngân",
                "Có 2 yêu cầu đã được Team Leader duyệt, đang chờ kế toán giải ngân.",
                NotificationType.SYSTEM, false);
        notif(accountant, "Kỳ lương tháng 5/2026 đã hoàn tất",
                "Hệ thống đã hoàn thành chi lương cho 4 nhân viên. Tổng: 65.500.000 ₫.",
                NotificationType.SYSTEM, true);

        // cfo
        notif(cfo, "Có 1 yêu cầu cấp ngân sách phòng ban mới",
                "Manager IT đã gửi yêu cầu DEPARTMENT_TOPUP 50.000.000 ₫ chờ phê duyệt.",
                NotificationType.SYSTEM, false);
        notif(cfo, "Yêu cầu ngân sách 100M đang chờ xử lý tự động",
                "Yêu cầu DEPARTMENT_TOPUP 100.000.000 ₫ đã được duyệt và đang chờ hệ thống chuyển tiền.",
                NotificationType.DEPT_TOPUP_APPROVED, true);

        // admin
        notif(admin, "Hệ thống hoạt động bình thường",
                "Tất cả dịch vụ đang hoạt động ổn định. Không có sự cố nào được ghi nhận.",
                NotificationType.SYSTEM, true);

        log.info("   ✅ 21 notifications created");
    }

    // ════════════════════════════════════════════════════════════════
    //  MODULE 7 — Audit logs
    // ════════════════════════════════════════════════════════════════

    private void seedAuditLogs(User admin, User accountant, User managerIT) {
        log.info("── [7] Audit logs");

        audit(admin,      AuditAction.UPDATE, "SystemConfig",  "SYSTEM_MAINTENANCE_MODE",
                "{\"value\":\"true\"}",  "{\"value\":\"false\"}");
        audit(admin,      AuditAction.UPDATE, "User",          "2",
                "{\"status\":\"ACTIVE\"}", "{\"status\":\"LOCKED\"}");
        audit(admin,      AuditAction.INSERT, "User",          "9",
                null, "{\"email\":\"emp.fin1@ifms.vn\",\"role\":\"EMPLOYEE\"}");
        audit(accountant, AuditAction.UPDATE, "PayrollPeriod", "1",
                "{\"status\":\"DRAFT\"}", "{\"status\":\"COMPLETED\"}");
        audit(accountant, AuditAction.INSERT, "PayrollPeriod", "2",
                null, "{\"month\":6,\"year\":2026,\"status\":\"DRAFT\"}");
        audit(managerIT,  AuditAction.INSERT, "Request",       "3",
                null, "{\"type\":\"DEPARTMENT_TOPUP\",\"amount\":50000000}");
        audit(admin,      AuditAction.UPDATE, "Department",    "2",
                "{\"totalProjectQuota\":15000000000}", "{\"totalProjectQuota\":20000000000}");
        audit(admin,      AuditAction.UPDATE, "SystemConfig",  "PIN_MAX_RETRY",
                "{\"value\":\"3\"}", "{\"value\":\"5\"}");
        audit(accountant, AuditAction.UPDATE, "Request",       "7",
                "{\"status\":\"APPROVED_BY_TEAM_LEADER\"}", "{\"status\":\"PAID\"}");
        audit(admin,      AuditAction.INSERT, "Department",    "4",
                null, "{\"code\":\"SALES\",\"name\":\"Phòng Kinh Doanh\"}");

        log.info("   ✅ 10 audit logs created");
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER BUILDERS
    // ════════════════════════════════════════════════════════════════

    private Request req(String code, User requester, Project project,
                        ProjectPhase phase, ExpenseCategory category,
                        RequestType type, BigDecimal amount,
                        BigDecimal approvedAmount, RequestStatus status,
                        String description) {
        return Request.builder()
                .requestCode(code)
                .requester(requester)
                .project(project).phase(phase).category(category)
                .type(type).amount(amount).approvedAmount(approvedAmount)
                .status(status).description(description)
                .build();
    }

    private void addHistory(Request request, User actor, RequestAction action,
                            RequestStatus statusAfter, String comment) {
        request.getHistories().add(RequestHistory.builder()
                .request(request).actor(actor)
                .action(action).statusAfterAction(statusAfter)
                .comment(comment)
                .build());
    }

    private Payslip payslip(PayrollPeriod period, User user, String empCode,
                            BigDecimal base, BigDecimal bonus, BigDecimal allowance,
                            BigDecimal deduction, BigDecimal advanceDeduct,
                            BigDecimal netSalary, int month, int year) {
        String code = codeGen.generate(BusinessCodeType.PAYSLIP,
                empCode, String.valueOf(month), String.valueOf(year));
        return Payslip.builder()
                .payslipCode(code).period(period).user(user)
                .baseSalary(base).bonus(bonus).allowance(allowance)
                .deduction(deduction).advanceDeduct(advanceDeduct)
                .finalNetSalary(netSalary)
                .status(PayslipStatus.PAID)
                .paymentDate(LocalDateTime.now().minusDays(1))
                .build();
    }

    private void notif(User user, String title, String message,
                       NotificationType type, boolean read) {
        notificationRepository.save(Notification.builder()
                .user(user).title(title).message(message)
                .type(type).isRead(read)
                .build());
    }

    private void audit(User actor, AuditAction action,
                       String entityName, String entityId,
                       String oldValues, String newValues) {
        auditLogRepository.save(AuditLog.builder()
                .traceId(UUID.randomUUID().toString())
                .actor(actor).action(action)
                .entityName(entityName).entityId(entityId)
                .oldValues(oldValues).newValues(newValues)
                .build());
    }

    // ════════════════════════════════════════════════════════════════
    //  LOOKUP HELPERS
    // ════════════════════════════════════════════════════════════════

    private User u(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }

    private Department dept(String code) {
        return departmentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Department not found: " + code));
    }

    private ExpenseCategory cat(String name) {
        return expenseCategoryRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Category not found: " + name));
    }
}
