package com.mkwang.backend.accounting;

import com.mkwang.backend.common.exception.BaseException;
import com.mkwang.backend.modules.accounting.dto.request.*;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.service.*;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.profile.entity.*;
import com.mkwang.backend.modules.project.entity.*;
import com.mkwang.backend.modules.request.dto.request.*;
import com.mkwang.backend.modules.request.dto.response.RequestDetailResponse;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.request.service.RequestService;
import com.mkwang.backend.modules.spreadsheet.service.SpreadsheetService;
import com.mkwang.backend.modules.user.entity.*;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.wallet.service.WalletService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real domain services and PostgreSQL. Fixtures exist only in an explicitly isolated database. */
@SpringBootTest(properties={"spring.main.web-application-type=servlet", "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.cache.type=none", "spring.datasource.hikari.maximum-pool-size=8", "logging.level.root=WARN", "logging.level.com.mkwang.backend=WARN", "logging.level.org.hibernate.SQL=WARN", "logging.level.org.hibernate.orm.jdbc.bind=WARN"})
@EnabledIfEnvironmentVariable(named="IFMS_TEST_DATABASE_URL",matches=".*ledger_review.*")
class FinancialWorkflowIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("IFMS_TEST_DATABASE_URL"));
        p.add("spring.datasource.username",()->System.getenv("IFMS_TEST_DATABASE_USER"));
        p.add("spring.datasource.password",()->System.getenv("IFMS_TEST_DATABASE_PASSWORD"));
        p.add("spring.data.redis.url",()->"redis://localhost:56379");
        p.add("spring.rabbitmq.addresses",()->"amqp://guest:guest@localhost:55672");
        p.add("app.ai.enabled",()->false);
    }
    @MockitoBean com.mkwang.backend.modules.notification.publisher.NotificationPublisher notifications;
    @MockitoBean com.mkwang.backend.modules.mail.publisher.MailPublisher mail;
    @MockitoBean com.mkwang.backend.modules.audit.publisher.AuditPublisher audit;
    @Autowired EntityManager em; @Autowired PlatformTransactionManager tm; @Autowired PasswordEncoder passwords;
    @Autowired RequestService requests; @Autowired WalletService wallets; @Autowired CompanyFundService fund;
    @Autowired PayrollManagementService payroll; @Autowired AccountingCorrectionService corrections; @Autowired SpreadsheetService excel;
    @Autowired AccountingCostReconciliationService costReconciliation; @Autowired AccountingOpeningService opening;
    @Autowired AccountingBackfillService history;
    TransactionTemplate tx; Map<String,User> actors; Long projectId,phaseId,categoryId; String suffix;
    static final String PIN="27055";
    static BigDecimal money(String n){return new BigDecimal(n);}
    @BeforeEach void prepare() {
        tx=new TransactionTemplate(tm);suffix=UUID.randomUUID().toString().substring(0,8);actors=new HashMap<>();
        tx.executeWithoutResult(status->{
            Department dept=Department.builder().name("Test finance "+suffix).code("T"+suffix).totalProjectQuota(money("10000")).build();em.persist(dept);
            for(String role:List.of("ADMIN","CFO","ACCOUNTANT","MANAGER","TEAM_LEADER","EMPLOYEE")) {
                Role r=em.createQuery("select r from Role r where r.name=:name",Role.class).setParameter("name",role).getSingleResult();
                User user=User.builder().email(role.toLowerCase()+"."+suffix+"@integration.invalid").fullName(role+" "+suffix).password(passwords.encode("IntegrationFixtureOnly!2026")).role(r).department(dept).isFirstLogin(false).build();
                user.setProfile(UserProfile.builder().user(user).employeeCode(role.substring(0,2)+suffix).build());
                user.setSecuritySettings(UserSecuritySettings.builder().user(user).transactionPin(passwords.encode(PIN)).build());
                em.persist(user);actors.put(role,user);wallets.createWallet(WalletOwnerType.USER,user.getId());
            }
            dept.setManager(actors.get("MANAGER"));wallets.createWallet(WalletOwnerType.DEPARTMENT,dept.getId());
            Project project=Project.builder().projectCode("IT"+suffix).name("Integration "+suffix).department(dept).manager(actors.get("MANAGER")).totalBudget(money("5000")).status(ProjectStatus.ACTIVE).build();em.persist(project);projectId=project.getId();wallets.createWallet(WalletOwnerType.PROJECT,projectId);
            ProjectPhase phase=ProjectPhase.builder().phaseCode("IP"+suffix).project(project).name("Active phase").budgetLimit(money("5000")).status(PhaseStatus.ACTIVE).build();em.persist(phase);phaseId=phase.getId();project.setCurrentPhase(phase);
            for(String role:List.of("TEAM_LEADER","EMPLOYEE")){User u=actors.get(role);em.persist(ProjectMember.builder().id(new ProjectMemberId(projectId,u.getId())).project(project).user(u).projectRole(role.equals("TEAM_LEADER")?ProjectRole.LEADER:ProjectRole.MEMBER).build());}
            ExpenseCategory cat=em.createQuery("select c from ExpenseCategory c where c.isSystemDefault=true",ExpenseCategory.class).setMaxResults(1).getSingleResult();categoryId=cat.getId();
            em.persist(PhaseCategoryBudget.builder().id(new PhaseCategoryBudgetId(phaseId,categoryId)).phase(phase).category(cat).budgetLimit(money("5000")).build());
        });
        login("CFO");SystemTopupRequest topup=new SystemTopupRequest();topup.setAmount(money("10000"));topup.setPaymentRef("IT-"+suffix);fund.topup(topup);
        login("MANAGER");Long deptReq=create(RequestType.DEPARTMENT_TOPUP,"5000",null,false).getId();
        login("CFO");requests.approveCfoRequest(deptReq,actorId(),new ApproveRequestRequest("integration",null));
        login("TEAM_LEADER");Long prjReq=create(RequestType.PROJECT_TOPUP,"5000",null,false).getId();
        login("MANAGER");requests.approveManagerRequest(prjReq,actorId(),new ApproveRequestRequest("integration",null));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void login(String role){var p=new UserDetailsAdapter(actors.get(role));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    Long actorId(){return ((UserDetailsAdapter)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser().getId();}
    RequestDetailResponse create(RequestType type,String amount,Long advance,boolean proof) {
        var r=new CreateRequestRequest();r.setType(type);r.setAmount(money(amount));r.setDescription("Integration evidence "+suffix);
        if(type!=RequestType.DEPARTMENT_TOPUP)r.setProjectId(projectId);
        if(List.of(RequestType.ADVANCE,RequestType.EXPENSE,RequestType.REIMBURSE).contains(type)){r.setPhaseId(phaseId);r.setCategoryId(categoryId);}
        r.setAdvanceBalanceId(advance);
        if(proof){var a=new AttachmentRequest();a.setFileName("proof.pdf");a.setCloudinaryPublicId("test/"+UUID.randomUUID());a.setUrl("https://integration.invalid/proof.pdf");a.setFileType("application/pdf");a.setSize(100L);r.setAttachments(List.of(a));}
        return requests.createRequest(r,actorId());
    }
    Long approved(RequestType type,String amount,Long advance,boolean proof){login("EMPLOYEE");var r=create(type,amount,advance,proof);login("TEAM_LEADER");requests.approveTlRequest(r.getId(),actorId(),new ApproveRequestRequest("integration",null));return r.getId();}
    List<VerifyFinancialDocumentRequest> documents(Long requestId,String amount){return tx.execute(status->{Request r=em.find(Request.class,requestId);return List.of(new VerifyFinancialDocumentRequest(r.getAttachmentFiles().getFirst().getId(),"ISSUER-"+suffix,"DOC-"+requestId,LocalDate.now(),money(amount),money(amount)));});}
    BigDecimal cost(){return tx.execute(s->em.find(Project.class,projectId).getTotalSpent());}
    BigDecimal userCash(){return wallets.getWallet(WalletOwnerType.USER,actors.get("EMPLOYEE").getId()).getBalance();}
    Long advance(Long requestId){return tx.execute(s->em.createQuery("select a.id from AdvanceBalance a where a.advanceRequest.id=:id",Long.class).setParameter("id",requestId).getSingleResult());}
    BigDecimal debt(Long id){return tx.execute(s->em.find(AdvanceBalance.class,id).getRemainingAmount());}
    Long journal(String event,Long requestId){return tx.execute(s->((Number)em.createNativeQuery("select id from accounting_journals where event_type=:e and request_id=:r").setParameter("e",event).setParameter("r",requestId).getSingleResult()).longValue());}

    @Test void expenseRecognizesCostOnceAndReversalRestoresAllSources() {
        Long id=approved(RequestType.EXPENSE,"100",null,true);login("ACCOUNTANT");
        requests.disburse(id,actorId(),new DisburseRequest(PIN,"Receipt accepted",documents(id,"100")));
        assertEquals(0,cost().compareTo(money("100")));assertEquals(0,userCash().signum());
        requests.payExpense(id,actorId(),new DisburseRequest(PIN,"Refund employee",List.of()));
        assertEquals(0,cost().compareTo(money("100")));assertEquals(0,userCash().compareTo(money("100")));
        var reversed=corrections.reverse(journal("EXPENSE_PAID",id),new ReverseAccountingJournalRequest("Invalid evidence confirmed by accountant",PIN,LocalDate.now(),null));
        assertEquals(2,reversed.size());assertEquals(0,cost().signum());assertEquals(0,userCash().signum());
        assertEquals(RequestStatus.REVERSED,tx.execute(s->em.find(Request.class,id).getStatus()));
        assertEquals(1,corrections.reverse(journal("EXPENSE_PAID",id),new ReverseAccountingJournalRequest("Idempotent retry after timeout",PIN,LocalDate.now(),null)).size());
    }
    @Test void advanceSettlementReturnAndPayrollKeepCostAndCashSeparate() {
        Long req=approved(RequestType.ADVANCE,"1000",null,false);login("ACCOUNTANT");requests.disburse(req,actorId(),new DisburseRequest(PIN,"Advance",List.of()));Long aid=advance(req);
        assertEquals(0,cost().signum());assertEquals(0,debt(aid).compareTo(money("1000")));
        Long settlement=approved(RequestType.REIMBURSE,"200",aid,true);login("ACCOUNTANT");requests.disburse(settlement,actorId(),new DisburseRequest(PIN,"Settle receipt",documents(settlement,"200")));
        assertEquals(0,cost().compareTo(money("200")));assertEquals(0,debt(aid).compareTo(money("800")));assertEquals(0,userCash().compareTo(money("1000")));
        login("EMPLOYEE");var ret=new AdvanceReturnRequest(money("300"),"Actual cash return","IT-"+suffix);requests.returnAdvanceBalance(aid,actorId(),ret);requests.returnAdvanceBalance(aid,actorId(),ret);
        assertEquals(0,debt(aid).compareTo(money("500")));assertEquals(0,userCash().compareTo(money("700")));
        login("ACCOUNTANT");Long period=tx.execute(s->{var p=PayrollPeriod.builder().periodCode("IT-"+suffix).name("Integration "+suffix).month(1).year(2099).build();em.persist(p);var slip=Payslip.builder().payslipCode("IT-"+suffix).period(p).user(em.find(User.class,actors.get("EMPLOYEE").getId())).baseSalary(money("1000")).build();p.getPayslips().add(slip);em.persist(slip);return p.getId();});
        payroll.autoNetting(period);payroll.runPayroll(period);
        assertEquals(0,debt(aid).signum());assertEquals(0,userCash().compareTo(money("1200")));assertEquals(0,cost().compareTo(money("200")));
        assertThrows(BaseException.class,()->payroll.confirmOverwrite(period));assertThrows(BaseException.class,()->payroll.runPayroll(period));
    }
    @Test void parallelDisbursementCreatesOnePaymentAndJournal() throws Exception {
        Long req=approved(RequestType.ADVANCE,"100",null,false);ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        Callable<Boolean> action=()->{login("ACCOUNTANT");gate.await();try{requests.disburse(req,actorId(),new DisburseRequest(PIN,"Parallel",List.of()));return true;}catch(BaseException e){return false;}finally{SecurityContextHolder.clearContext();}};
        var a=executor.submit(action);var b=executor.submit(action);gate.countDown();try{assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
        assertEquals(0,userCash().compareTo(money("100")));assertNotNull(journal("ADVANCE_DISBURSED",req));
    }
    @Test void excelRejectsFormulaAndCommitsWholeBatchIdempotently() throws Exception {
        login("ADMIN");byte[] template=excel.template("departments",null);byte[] valid;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(template));var out=new ByteArrayOutputStream()){
            var sheet=book.getSheet("Data");var row=sheet.createRow(1);row.createCell(0).setCellValue("EX"+suffix);row.createCell(1).setCellValue("Excel "+suffix);book.write(out);valid=out.toByteArray();
        }
        var preview=excel.preview("departments",null,new MockMultipartFile("file","departments.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",valid));
        assertTrue(preview.errors().isEmpty(),preview.errors().toString());assertEquals("COMMITTED",excel.commit(preview.batchId()).state());assertEquals("COMMITTED",excel.commit(preview.batchId()).state());
        byte[] invalid;try(var book=new XSSFWorkbook(new ByteArrayInputStream(template));var out=new ByteArrayOutputStream()){
            var row=book.getSheet("Data").createRow(1);row.createCell(0).setCellFormula("1+1");row.createCell(1).setCellValue("Must not be inserted "+suffix);book.write(out);invalid=out.toByteArray();
        }
        var rejected=excel.preview("departments",null,new MockMultipartFile("file","bad.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",invalid));
        assertFalse(rejected.errors().isEmpty());assertNotEquals("COMMITTED",excel.commit(rejected.batchId()).state());
        login("EMPLOYEE");assertThrows(BaseException.class,()->excel.template("departments",null));
    }
    @Test void excelReallocatesCategoryLimitsInOneAtomicBatch() throws Exception {
        Long second=tx.execute(status->em.createQuery("select c.id from ExpenseCategory c where c.isSystemDefault=true and c.id<>:id",Long.class)
                .setParameter("id",categoryId).setMaxResults(1).getSingleResult());
        login("TEAM_LEADER");byte[] template=excel.template("category-budgets",projectId);byte[] upload;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(template));var out=new ByteArrayOutputStream()){
            var sheet=book.getSheet("Data");var increase=sheet.createRow(1);
            increase.createCell(0).setCellValue(phaseId.toString());increase.createCell(1).setCellValue(second.toString());increase.createCell(2).setCellValue(1000);
            var decrease=sheet.createRow(2);decrease.createCell(0).setCellValue(phaseId.toString());decrease.createCell(1).setCellValue(categoryId.toString());decrease.createCell(2).setCellValue(4000);
            book.write(out);upload=out.toByteArray();
        }
        var preview=excel.preview("category-budgets",projectId,new MockMultipartFile("file","budgets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",upload));
        assertTrue(preview.errors().isEmpty(),preview.errors().toString());
        assertEquals("COMMITTED",excel.commit(preview.batchId()).state());
        assertEquals(0,tx.execute(s->em.find(PhaseCategoryBudget.class,new PhaseCategoryBudgetId(phaseId,categoryId)).getBudgetLimit()).compareTo(money("4000")));
        assertEquals(0,tx.execute(s->em.find(PhaseCategoryBudget.class,new PhaseCategoryBudgetId(phaseId,second)).getBudgetLimit()).compareTo(money("1000")));
    }
    @Test void costCountersOnlyReconcileAgainstVerifiedDocumentsAndJournal() {
        Long id=approved(RequestType.EXPENSE,"100",null,true);login("ACCOUNTANT");
        requests.disburse(id,actorId(),new DisburseRequest(PIN,"Receipt accepted",documents(id,"100")));
        assertTrue(costReconciliation.preview(projectId).eligible());
        tx.executeWithoutResult(status->{
            var project=em.find(Project.class,projectId);project.setTotalSpent(money("200"));
            project.setAvailableBudget(project.getAvailableBudget().subtract(money("100")));
            em.find(ProjectPhase.class,phaseId).setCurrentSpent(money("200"));
            em.find(PhaseCategoryBudget.class,new PhaseCategoryBudgetId(phaseId,categoryId)).setCurrentSpent(money("200"));
        });
        var preview=costReconciliation.preview(projectId);
        assertTrue(preview.eligible(),preview.errors().toString());
        assertEquals(0,preview.verifiedCost().compareTo(money("100")));
        assertEquals(0,preview.current().projectCost().compareTo(money("200")));
        var result=costReconciliation.apply(new ReconcileCostCountersRequest(projectId,preview.current().projectCost(),
                preview.current().availableBudget(),preview.verifiedCost(),"Verified source and journal "+suffix,PIN,true));
        assertTrue(result.eligible(),result.errors().toString());assertEquals(0,cost().compareTo(money("100")));
        assertThrows(BaseException.class,()->costReconciliation.apply(new ReconcileCostCountersRequest(projectId,preview.current().projectCost(),
                preview.current().availableBudget(),preview.verifiedCost(),"Stale preview must fail",PIN,true)));
    }
    @Test void openingRecordsOnlyLegacyGapAndBlocksLaterAutomaticBackfill() {
        login("ACCOUNTANT");
        tx.executeWithoutResult(status->em.createNativeQuery("UPDATE wallets SET balance=balance+100 WHERE owner_type='PROJECT' AND owner_id=:project")
                .setParameter("project",projectId).executeUpdate());
        var preview=opening.preview("PROJECT",projectId);
        assertEquals(0,preview.difference().compareTo(money("100")));
        var posted=opening.commit(new OpeningBalanceRequest("PROJECT",projectId,preview.sourceBalance(),preview.recordedBalance(),
                "Legacy balance confirmation "+suffix,PIN,true));
        assertEquals(AccountingJournalEvent.OPENING_BALANCE,posted.eventType());
        assertEquals(posted.id(),opening.commit(new OpeningBalanceRequest("PROJECT",projectId,preview.sourceBalance(),preview.recordedBalance(),
                "Idempotent legacy balance confirmation",PIN,true)).id());
        assertThrows(BaseException.class,()->history.preview(new BackfillJournalBatchRequest(List.of(),true)));
    }
}
