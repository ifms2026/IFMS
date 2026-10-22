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
    @Autowired com.mkwang.backend.modules.accounting.service.BankEvidenceService bankEvidence;
    @Autowired PayrollManagementService payroll; @Autowired AccountingCorrectionService corrections; @Autowired SpreadsheetService excel;
    @Autowired AccountingCostReconciliationService costReconciliation; @Autowired AccountingOpeningService opening;
    @Autowired AccountingBackfillService history;
    @Autowired com.mkwang.backend.modules.spreadsheet.service.FinancialLookupService lookups;
    @Autowired AccountingJournalService journalService;
    @Autowired AccountingReportService reports;
    @Autowired com.mkwang.backend.modules.audit.service.AuditLogService audits;
    @Autowired com.mkwang.backend.modules.profile.service.ProfileService profiles;
    @Autowired AccountingPeriodService periods;
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
        login("CFO");
        tx.executeWithoutResult(status -> em.createNativeQuery("UPDATE company_funds SET bank_account='IFMS-TEST' WHERE id=1").executeUpdate());
        Long evidenceId=bankEvidence.importLine("IFMS-TEST","IT-"+suffix,LocalDate.now(),"IN",money("10000"),"BOOKED","integration fixture",actorId(),null);
        SystemTopupRequest topup=new SystemTopupRequest();topup.setAmount(money("10000"));topup.setBankEvidenceId(evidenceId);fund.topup(topup);
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
    int exportedRows(byte[] bytes) throws Exception {try(var b=new XSSFWorkbook(new ByteArrayInputStream(bytes))){return Integer.parseInt(b.getSheet("ThongTin").getRow(3).getCell(0).getStringCellValue().replace("Số dòng: ",""));}}
    @Test void accountEventAndJournalRequestFiltersMatchExport() throws Exception {
        Long id=approved(RequestType.EXPENSE,"100",null,true);login("ACCOUNTANT");requests.disburse(id,actorId(),new DisburseRequest(PIN,"Verified",documents(id,"100")));
        Long ar=approved(RequestType.ADVANCE,"400",null,false);login("ACCOUNTANT");requests.disburse(ar,actorId(),new DisburseRequest(PIN,"Advance",List.of()));
        var filtered=reports.getLedger(null,null,null,null,projectId,null,null,null,1,20,AccountingJournalEvent.EXPENSE_VERIFIED);
        assertEquals(0,filtered.totalDebit().compareTo(money("100")));
        var filter=Map.of("account","PROJECT_EXPENSE","projectId",projectId.toString(),"event","EXPENSE_VERIFIED");
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(excel.export("account-balances",null,filter)))){assertEquals(100.0,b.getSheet("Data").getRow(1).getCell(3).getNumericCellValue());}
        assertEquals(2,exportedRows(excel.export("journals",null,Map.of("requestId",id.toString(),"event","EXPENSE_VERIFIED"))));
        assertEquals(0,journalService.getBudgetPage(suffix,1,1).getItems().getFirst().recognizedExpense().compareTo(money("100")));
        assertEquals(1,journalService.getOutstandingPage(actors.get("EMPLOYEE").getFullName(),1,1).getTotal());
        login("MANAGER");
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(excel.export("project-cost-details",null,Map.of("projectId",projectId.toString()))))){assertEquals(100.0,b.getSheet("Data").getRow(1).getCell(4).getNumericCellValue());assertEquals(100.0,b.getSheet("Data").getRow(1).getCell(5).getNumericCellValue());}
        assertEquals(1,exportedRows(excel.export("department-budgets",null,Map.of())));
        assertTrue(exportedRows(excel.export("department-employees",null,Map.of()))>0);
        Long ownFunding=tx.execute(status->em.createQuery("select r.id from Request r where r.type='DEPARTMENT_TOPUP' and r.requester.id=:id",Long.class).setParameter("id",actors.get("MANAGER").getId()).getSingleResult());
        assertEquals(1,lookups.find("request",null,ownFunding,null,1,20).getTotal());
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(excel.export("managed-requests",null,Map.of())))){boolean found=false;for(var row:b.getSheet("Data"))if(row.getRowNum()>0&&row.getCell(1)!=null&&"DEPARTMENT_TOPUP".equals(row.getCell(1).toString()))found=true;assertTrue(found);}
        login("ACCOUNTANT");
        tx.executeWithoutResult(status->{for(int n=0;n<2;n++){var p=Project.builder().projectCode("PG"+suffix+n).name("Paging "+suffix).department(em.find(Department.class,actors.get("MANAGER").getDepartment().getId())).manager(em.find(User.class,actors.get("MANAGER").getId())).build();em.persist(p);wallets.createWallet(WalletOwnerType.PROJECT,p.getId());}});
        var first=journalService.getBudgetPage("Paging "+suffix,1,1);var second=journalService.getBudgetPage("Paging "+suffix,2,1);assertEquals(2,first.getTotal());assertNotEquals(first.getItems().getFirst().projectId(),second.getItems().getFirst().projectId());
        login("CFO");assertEquals(1,exportedRows(excel.export("bank-reconciliation-summary",null,Map.of())));
    }
    @Test void scopedLookupsAndExportsCannotWidenEmployeeManagerOrLeaderScope() throws Exception {
        User self=actors.get("EMPLOYEE");
        User other=tx.execute(status->{var u=User.builder().email("other."+suffix+"@integration.invalid").fullName("Other "+suffix).password(passwords.encode("FixtureOnly!2026")).role(em.find(Role.class,self.getRole().getId())).department(em.find(Department.class,self.getDepartment().getId())).status(UserStatus.ACTIVE).isFirstLogin(false).build();u.setProfile(UserProfile.builder().user(u).employeeCode("OT"+suffix).build());u.setSecuritySettings(UserSecuritySettings.builder().user(u).transactionPin(passwords.encode(PIN)).build());em.persist(u);wallets.createWallet(WalletOwnerType.USER,u.getId());em.persist(ProjectMember.builder().id(new ProjectMemberId(projectId,u.getId())).project(em.find(Project.class,projectId)).user(u).projectRole(ProjectRole.MEMBER).build());return u;});
        actors.put("EMPLOYEE",other);Long request=approved(RequestType.ADVANCE,"200",null,false);login("ACCOUNTANT");requests.disburse(request,actorId(),new DisburseRequest(PIN,"Other employee advance",List.of()));actors.put("EMPLOYEE",self);
        login("EMPLOYEE");assertEquals(0,exportedRows(excel.export("advance-balances",null,Map.of("employeeId",other.getId().toString()))));assertEquals(0,lookups.find("employee",null,other.getId(),null,1,20).getTotal());
        Long[] foreign=tx.execute(status->{var d=Department.builder().code("F"+suffix).name("Foreign "+suffix).build();em.persist(d);var p=Project.builder().projectCode("FP"+suffix).name("Foreign project "+suffix).department(d).manager(em.find(User.class,actors.get("MANAGER").getId())).build();em.persist(p);em.find(Request.class,request).setProject(p);em.find(User.class,other.getId()).setDepartment(d);return new Long[]{d.getId(),p.getId()};});
        login("MANAGER");assertEquals(0,exportedRows(excel.export("advance-balances",null,Map.of("departmentId",foreign[0].toString()))));assertEquals(0,exportedRows(excel.export("department-employees",null,Map.of("departmentId",foreign[0].toString()))));assertEquals(0,lookups.find("project",null,foreign[1],null,1,20).getTotal());
        login("TEAM_LEADER");assertEquals(0,exportedRows(excel.export("advance-balances",null,Map.of("projectId",foreign[1].toString()))));assertEquals(0,lookups.find("project",null,foreign[1],null,1,20).getTotal());assertTrue(lookups.find("employee",self.getFullName(),null,null,1,20).getItems().stream().anyMatch(o->o.id().equals(self.getId())));
        login("CFO");assertEquals(1,exportedRows(excel.export("advance-balances",null,Map.of("projectId",foreign[1].toString()))));
        assertEquals(1,lookups.find("phase",null,phaseId,projectId,1,20).getTotal());assertEquals(1,lookups.find("category",null,categoryId,phaseId,1,20).getTotal());
    }
    @Test void auditExportUsesSameActorActionAndDateFiltersAcrossAllPages() throws Exception {
        tx.executeWithoutResult(status->{for(int n=0;n<25;n++)em.persist(com.mkwang.backend.modules.audit.entity.AuditLog.builder().actor(em.find(User.class,actors.get("EMPLOYEE").getId())).traceId(UUID.randomUUID().toString()).action(com.mkwang.backend.modules.audit.entity.AuditAction.UPDATE).entityName("ReviewFixture").entityId(suffix+"-"+n).build());});
        login("ADMIN");String actor=actors.get("EMPLOYEE").getFullName();
        var page=audits.getAuditLogs(null,actor,com.mkwang.backend.modules.audit.entity.AuditAction.UPDATE,null,LocalDate.now(),LocalDate.now(),1,10);
        assertEquals(25,page.getTotal());assertEquals(10,page.getItems().size());
        assertEquals(25,exportedRows(excel.export("audit-history",null,Map.of("actor",actor,"action","UPDATE","from",LocalDate.now().toString(),"to",LocalDate.now().toString()))));
        assertEquals(0,exportedRows(excel.export("audit-history",null,Map.of("actor",actor,"action","DELETE"))));assertFalse(excel.exportHistory().isEmpty());
    }
    @Test void reconciliationCandidatesExecuteOnPostgresAndFindUnmatchedMovement(){
        login("CFO");var transfer=wallets.systemTopup(money("123"),"UNMATCH-"+suffix,"Isolated fixture");
        Long line=bankEvidence.importLine("IFMS-TEST","CAND-"+suffix,LocalDate.now(),"IN",money("123"),"BOOKED","fixture",actorId(),null);
        assertTrue(bankEvidence.candidates(line).stream().anyMatch(row->row.id().equals(transfer.getId())));
    }
    @Test void concurrentFailedPinsAreCountedWithoutLostUpdates() throws Exception {
        login("ACCOUNTANT");Long user=actorId();ExecutorService executor=Executors.newFixedThreadPool(5);CountDownLatch gate=new CountDownLatch(1);var futures=new ArrayList<Future<?>>();
        for(int n=0;n<5;n++)futures.add(executor.submit(()->{login("ACCOUNTANT");try{gate.await();profiles.verifyMyPin(user,new com.mkwang.backend.modules.profile.dto.request.VerifyMyPinRequest("99999"));}catch(com.mkwang.backend.common.exception.LockedException expected){}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{SecurityContextHolder.clearContext();}}));
        gate.countDown();try{for(var f:futures)f.get(30,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
        assertEquals(Integer.valueOf(5),tx.<Integer>execute(status->em.find(UserSecuritySettings.class,user).getRetryCount()));assertTrue(Boolean.TRUE.equals(tx.<Boolean>execute(status->em.find(UserSecuritySettings.class,user).isPinLocked())));
    }
    @Test void currentAccountingMonthCannotClose(){login("ACCOUNTANT");String month=YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();assertFalse(periods.previewClose(month).eligible());assertThrows(BaseException.class,()->periods.close(month,"Current month cannot close",true));}

}
