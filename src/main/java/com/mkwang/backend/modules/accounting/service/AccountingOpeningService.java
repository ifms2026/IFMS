package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.dto.request.OpeningBalanceRequest;
import com.mkwang.backend.modules.accounting.dto.response.*;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.AccountingJournalRepository;
import com.mkwang.backend.modules.accounting.mapper.AccountingJournalMapper;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.profile.service.ProfileService;
import com.mkwang.backend.modules.profile.dto.request.VerifyMyPinRequest;
import com.mkwang.backend.modules.request.service.*;
import com.mkwang.backend.modules.wallet.service.WalletFinancialEvidenceService;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class AccountingOpeningService {
    private final AccountingJournalService accounting;
    private final AccountingJournalRepository journals;private final AccountingJournalMapper mapper;
    private final WalletFinancialEvidenceService wallets;private final RequestHistoricalEvidenceService sources;
    private final RequestAccountingContextService contexts;private final ProfileService profiles;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    @PersistenceContext private EntityManager em;
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<Map<String,Object>> scopes(){
        var result=new ArrayList<>(wallets.openingScopes());
        for(var employee:accounting.getOutstandingAdvancesByEmployee())for(var advance:employee.advances())
            result.add(com.mkwang.backend.modules.spreadsheet.service.ExcelValues.values("kind","ADVANCE","id",advance.id(),"balance",advance.remainingAmount(),"label",employee.employeeName()+" · "+advance.requestCode()));
        return result;
    }
    @Transactional @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public OpeningBalancePreviewResponse preview(String kind,Long id) {return inspect(kind,id);}
    private OpeningBalancePreviewResponse inspect(String kind,Long id) {
        String account,label,dimension="";BigDecimal actual;
        if("ADVANCE".equals(kind)) {
            var balance=sources.lockAdvance(id);actual=balance.getRemainingAmount();account="ADVANCE_RECEIVABLE";
            label=balance.getUser().getFullName()+" · "+balance.getAdvanceRequest().getRequestCode();dimension=" AND advance_balance_id=:scope";
        } else {
            WalletOwnerType type;
            try{type=WalletOwnerType.valueOf(kind);}catch(IllegalArgumentException e){throw new BadRequestException("Phạm vi mở sổ không hợp lệ");}
            actual=wallets.lockBalance(type,id);
            account=switch(type){case COMPANY_FUND->"COMPANY_FUND_WALLET";case DEPARTMENT->"DEPARTMENT_WALLET";case PROJECT->"PROJECT_WALLET";default->throw new BadRequestException("Phạm vi mở sổ không hợp lệ");};
            label=switch(type){case COMPANY_FUND->"Quỹ công ty";case DEPARTMENT->"Ví phòng ban #"+id;case PROJECT->"Ví dự án #"+id;default->"";};
            dimension=type==WalletOwnerType.DEPARTMENT?" AND department_id=:scope":type==WalletOwnerType.PROJECT?" AND project_id=:scope":"";
        }
        var query=em.createNativeQuery("SELECT COALESCE(SUM(debit_amount-credit_amount),0) FROM accounting_journal_lines WHERE account_code=:account"+dimension).setParameter("account",account);
        if(!dimension.isEmpty())query.setParameter("scope",id);
        BigDecimal recorded=(BigDecimal)query.getSingleResult();
        var existing=journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.OPENING_BALANCE,"OPENING_SCOPE",kind+":"+id);
        return new OpeningBalancePreviewResponse(kind,id,account,label,actual,recorded,actual.subtract(recorded),existing.map(AccountingJournal::getId).orElse(null));
    }
    @Transactional @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountingJournalDetailResponse commit(OpeningBalanceRequest input) {
        var actor=((UserDetailsAdapter)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        if(!profiles.verifyMyPin(actor.getId(),new VerifyMyPinRequest(input.pin())).isValid())throw new UnauthorizedException("PIN không đúng");
        em.createNativeQuery("SELECT pg_advisory_xact_lock(89654322)").getSingleResult();
        var existing=journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.OPENING_BALANCE,"OPENING_SCOPE",input.kind()+":"+input.sourceId());
        if(existing.isPresent())return mapper.toDetail(existing.get());
        if(!input.historicalReviewComplete())throw new BadRequestException("Hoàn thành ghi bù và đối chiếu lịch sử trước khi mở sổ");
        var preview=inspect(input.kind(),input.sourceId());
        if(preview.sourceBalance().compareTo(input.expectedSourceBalance())!=0 || preview.recordedBalance().compareTo(input.expectedRecordedBalance())!=0)
            throw new BadRequestException("Số dư đã thay đổi; cần xem trước và đối chiếu lại");
        if(preview.difference().signum()==0)throw new BadRequestException("Sổ đã khớp nguồn, không tạo bút toán mở sổ bằng 0");
        var today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String snapshot;try{snapshot=json.writeValueAsString(Map.of("sourceBalance",preview.sourceBalance(),"recordedBefore",preview.recordedBalance(),"evidenceRef",input.evidenceRef(),"cutoverAt",Instant.now().toString(),"coverage","OPENING_GAP_ONLY"));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new InternalSystemException("Không lưu được căn cứ mở sổ");}
        var context="ADVANCE".equals(input.kind())?contexts.forAdvance(input.sourceId()):null;
        var j=AccountingJournal.builder().journalCode("JRN-O-"+UUID.randomUUID().toString().substring(0,20)).eventType(AccountingJournalEvent.OPENING_BALANCE)
            .postingDate(today).businessDate(today).postingPeriod(YearMonth.from(today).toString()).sourceType("OPENING_SCOPE").sourceId(input.kind()+":"+input.sourceId())
            .description("Mở sổ phần chênh lệch đã xác minh · "+preview.label()).totalAmount(preview.difference().abs()).sourceSnapshot(snapshot)
            .advanceBalanceId(context==null?null:input.sourceId()).employeeId(context==null?null:context.employeeId()).employeeName(context==null?null:context.employeeName())
            .projectId(context==null?("PROJECT".equals(input.kind())?input.sourceId():null):context.projectId())
            .departmentId(context==null?("DEPARTMENT".equals(input.kind())?input.sourceId():null):context.departmentId())
            .phaseId(context==null?null:context.phaseId()).categoryId(context==null?null:context.categoryId())
            .projectName(context==null?null:context.projectName()).departmentName(context==null?null:context.departmentName())
            .phaseName(context==null?null:context.phaseName()).categoryName(context==null?null:context.categoryName())
            .createdByUserId(actor.getId()).createdByName(actor.getFullName()).createdAt(Instant.now()).build();
        var line=AccountingJournalLine.builder().journal(j).lineNumber(1).accountCode(preview.accountCode()).accountName(InternalAccount.require(preview.accountCode()).label())
            .debitAmount(preview.difference().max(BigDecimal.ZERO)).creditAmount(preview.difference().negate().max(BigDecimal.ZERO)).effectDescription("Phần chênh lệch mở sổ có chứng cứ")
            .advanceBalanceId(context==null?null:input.sourceId()).employeeId(context==null?null:context.employeeId())
            .projectId(context==null?("PROJECT".equals(input.kind())?input.sourceId():null):context.projectId())
            .departmentId(context==null?("DEPARTMENT".equals(input.kind())?input.sourceId():null):context.departmentId())
            .phaseId(context==null?null:context.phaseId()).categoryId(context==null?null:context.categoryId())
            .projectName(context==null?null:context.projectName()).phaseName(context==null?null:context.phaseName())
            .categoryName(context==null?null:context.categoryName()).build();
        j.getLines().add(line);j.getLines().add(AccountingJournalLine.builder().journal(j).lineNumber(2).accountCode("OPENING_BALANCE_CLEARING").accountName(InternalAccount.OPENING_BALANCE_CLEARING.label())
            .debitAmount(line.getCreditAmount()).creditAmount(line.getDebitAmount()).effectDescription("Đối ứng phần chênh lệch mở sổ, không phải chi phí").build());
        return mapper.toDetail(journals.save(j));
    }
}
