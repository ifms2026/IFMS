package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.dto.request.BackfillJournalBatchRequest;
import com.mkwang.backend.modules.accounting.dto.request.BackfillJournalBatchRequest.OperationRequest;
import com.mkwang.backend.modules.accounting.dto.response.BackfillJournalPreviewResponse;
import com.mkwang.backend.modules.accounting.dto.response.BackfillJournalPreviewResponse.OperationResponse;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse;
import com.mkwang.backend.modules.request.service.RequestHistoricalEvidenceService;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.wallet.service.WalletFinancialEvidenceService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountingBackfillService {
    private final AccountingJournalRepository journals;private final PayslipRepository payslips;
    private final RequestHistoricalEvidenceService sources;private final WalletFinancialEvidenceService wallets;
    private final AccountingJournalService accounting;private final FinancialDocumentService documents;private final BankEvidenceService bank;
    @PersistenceContext private EntityManager em;
    private record Prepared(OperationRequest input,Request request,AdvanceBalance advance,Payslip payslip,Transaction transaction,BigDecimal amount,String code,boolean recorded) {}
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<Map<String,Object>> employeeAdvances(Long employeeId){return sources.employeeAdvances(employeeId);}
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<Map<String,Object>> candidates(String search) {
        if(search==null||search.trim().length()<3) return List.of();
        search=search.trim();
        var result=new ArrayList<Map<String,Object>>();
        for(var r:sources.historicalRequests(search,500)) {
            String event=switch(r.type()){case "ADVANCE"->"ADVANCE_DISBURSED";case "EXPENSE"->"PAID".equals(r.status())?"EXPENSE_PAID":"EXPENSE_VERIFIED";case "REIMBURSE"->"REIMBURSE_SETTLED";case "DEPARTMENT_TOPUP"->"DEPARTMENT_ALLOCATION";case "PROJECT_TOPUP"->"PROJECT_ALLOCATION";default->null;};
            if(event!=null && journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.valueOf(event),"REQUEST",r.requestId().toString()).isEmpty())
                result.add(com.mkwang.backend.modules.spreadsheet.service.ExcelValues.values("kind","REQUEST","sourceId",r.requestId(),"code",r.requestCode(),"event",event,"amount",r.approvedAmount(),"businessDate",r.businessDate()==null?null:r.businessDate().toString(),"employee",r.employeeName(),"documents",r.documents()));
        }
        for(var row:wallets.historicalTransactions(search,500)) if(journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.valueOf((String)row.get("event")),"TRANSACTION",row.get("sourceId").toString()).isEmpty())result.add(row);
        var payrollPage=payslips.findHistoricalAccountingSources(search,org.springframework.data.domain.PageRequest.of(0,501));
        if(payrollPage.getTotalElements()>500)throw new BadRequestException("Có quá nhiều phiếu lương trùng mã tìm kiếm; nhập mã cụ thể hơn");
        for(var p:payrollPage)
            if(p.getStatus()==PayslipStatus.PAID && journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.PAYROLL_SETTLEMENT,"PAYSLIP",p.getId().toString()).isEmpty())
                result.add(com.mkwang.backend.modules.spreadsheet.service.ExcelValues.values("kind","PAYSLIP","sourceId",p.getId(),"code",p.getPayslipCode(),"event","PAYROLL_SETTLEMENT","amount",p.getBaseSalary().add(p.getBonus()).add(p.getAllowance()),"advanceDeduct",p.getAdvanceDeduct(),"employeeId",p.getUser().getId(),"employee",p.getUser().getFullName(),"businessDate",p.getPaymentDate()==null?null:p.getPaymentDate().toLocalDate().toString(),"documents",List.of()));
        if(result.size()>500)throw new BadRequestException("Có quá nhiều nguồn lịch sử; nhập mã cụ thể hơn");
        return result;
    }
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public BackfillJournalPreviewResponse preview(BackfillJournalBatchRequest input) {return inspect(input,false);}
    @Transactional
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public BackfillJournalPreviewResponse commit(BackfillJournalBatchRequest input) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(89654322)").getSingleResult();
        if(input.operations().stream().anyMatch(op->op.event()==AccountingJournalEvent.PAYROLL_SETTLEMENT))
            em.createNativeQuery("SELECT pg_advisory_xact_lock(89654321)").getSingleResult();
        return inspect(input,true);
    }
    private BackfillJournalPreviewResponse inspect(BackfillJournalBatchRequest batch,boolean commit) {
        if(!batch.acknowledgeHistoricalEvidence())throw new BadRequestException("Cần xác nhận đã đối chiếu ý nghĩa nghiệp vụ và chứng từ lịch sử");
        if(((Number)em.createNativeQuery("SELECT COUNT(*) FROM accounting_journals WHERE event_type='OPENING_BALANCE'").getSingleResult()).longValue()>0)
            throw new BadRequestException("Đã xác nhận mở sổ chuyển đổi. Không ghi bù lịch sử tự động sau mốc này vì có thể ghi trùng phần mở sổ; cần đối chiếu điều chỉnh có chứng từ riêng");
        List<String> errors=new ArrayList<>();List<Prepared> prepared=new ArrayList<>();Set<String> unique=new HashSet<>();
        Map<Long,BigDecimal> advanceEffects=new HashMap<>();Map<Long,BigDecimal> legacyOffsets=new HashMap<>();
        for(var input:batch.operations()) {
            try {
                if(!unique.add(input.event()+":"+input.sourceId()))throw new BadRequestException("Nguồn bị chọn trùng trong lô");
                var source=prepare(input,commit);prepared.add(source);
                if(source.recorded())continue;
                if(source.advance()!=null)advanceEffects.merge(source.advance().getId(),input.event()==AccountingJournalEvent.ADVANCE_DISBURSED?source.amount():source.amount().negate(),BigDecimal::add);
                if(source.payslip()!=null&&source.payslip().getAdvanceDeduct().signum()>0) {
                    for(var allocation:input.payrollAllocations()) {
                        advanceEffects.merge(allocation.advanceBalanceId(),allocation.amount().negate(),BigDecimal::add);
                        legacyOffsets.merge(allocation.advanceBalanceId(),allocation.amount(),BigDecimal::add);
                    }
                }
            } catch(BaseException error){errors.add(input.event()+" · "+input.sourceId()+": "+error.getMessage());}
        }
        for(var effect:advanceEffects.entrySet()) {
            var advance=sources.advance(effect.getKey());
            BigDecimal recorded=(BigDecimal)em.createNativeQuery("SELECT COALESCE(SUM(debit_amount-credit_amount),0) FROM accounting_journal_lines WHERE advance_balance_id=:id AND account_code='ADVANCE_RECEIVABLE'")
                    .setParameter("id",effect.getKey()).getSingleResult();
            if(recorded.add(effect.getValue()).compareTo(advance.getRemainingAmount())!=0) errors.add("Tạm ứng "+advance.getAdvanceRequest().getRequestCode()+": lô chưa đủ chứng cứ để khớp toàn bộ giải ngân/quyết toán/hoàn tiền/khấu trừ với dư hiện tại");
            if(legacyOffsets.getOrDefault(effect.getKey(),BigDecimal.ZERO).compareTo(advance.getLegacyUnclassifiedAmount())>0) errors.add("Tạm ứng "+advance.getAdvanceRequest().getRequestCode()+": không đủ lịch sử chưa phân loại để xác nhận payroll");
        }
        var rows=prepared.stream().map(p->new OperationResponse(p.input().event().name(),p.input().sourceId(),p.code(),p.amount(),p.recorded())).toList();
        if(!errors.isEmpty()||!commit)return new BackfillJournalPreviewResponse(errors.isEmpty(),rows,errors);
        Long actor=((UserDetailsAdapter)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser().getId();
        // Source events are ordered before settlement events; all write or none do.
        for(var source:prepared.stream().sorted(Comparator.comparingInt(p->p.input().event()==AccountingJournalEvent.ADVANCE_DISBURSED?0:1)).toList()) {
            if(source.recorded())continue;
            var input=source.input();
            try(var ignored=AccountingPostingContext.open(input.postingDate(),input.businessDate(),input.evidenceRef())) {
                switch(input.event()) {
                    case ADVANCE_DISBURSED -> accounting.recordAdvanceDisbursed(source.request(),source.advance(),source.transaction());
                    case EXPENSE_VERIFIED,EXPENSE_PAID -> {
                        if(journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.EXPENSE_VERIFIED,"REQUEST",input.sourceId().toString()).isEmpty()) {
                            if(!documents.hasAllocation(source.request().getId(),source.amount()))documents.allocate(source.request().getId(),source.amount(),actor,input.documents());
                            accounting.recordExpenseVerified(source.request());
                        }
                        if(source.request().getStatus()==RequestStatus.PAID)accounting.recordExpensePaid(source.request(),source.transaction());
                    }
                    case REIMBURSE_SETTLED -> {
                        if(!documents.hasAllocation(source.request().getId(),source.amount()))documents.allocate(source.request().getId(),source.amount(),actor,input.documents());
                        accounting.recordReimburseSettled(source.request(),source.advance());
                    }
                    case DEPARTMENT_ALLOCATION -> accounting.recordDepartmentAllocation(source.request(),source.transaction(),source.request().getRequester().getDepartment().getName());
                    case PROJECT_ALLOCATION -> accounting.recordProjectAllocation(source.request(),source.transaction(),source.request().getProject().getDepartment().getName());
                    case ADVANCE_RETURNED -> accounting.recordAdvanceReturned(source.advance(),source.transaction(),source.amount(),source.transaction().getDescription());
                    case SYSTEM_TOPUP -> accounting.recordSystemTopup(source.transaction());
                    case PAYROLL_SETTLEMENT -> {
                        var allocations=input.payrollAllocations()==null?List.<AdvanceSettlementAllocationResponse>of():input.payrollAllocations().stream()
                                .map(a->new AdvanceSettlementAllocationResponse(a.advanceBalanceId(),a.amount())).toList();
                        for(var allocation:allocations)sources.classifyPayroll(allocation.advanceBalanceId(),source.payslip().getUser().getId(),allocation.amount());
                        accounting.recordPayrollSettlement(source.payslip(),source.transaction(),allocations);
                    }
                    default -> throw new BadRequestException("Loại nguồn lịch sử không được hỗ trợ");
                }
            }
        }
        return new BackfillJournalPreviewResponse(true,rows,List.of());
    }
    private Prepared prepare(OperationRequest input,boolean lock) {
        String sourceType=switch(input.event()){case SYSTEM_TOPUP,ADVANCE_RETURNED->"TRANSACTION";case PAYROLL_SETTLEMENT->"PAYSLIP";default->"REQUEST";};
        boolean recorded=journals.findByEventTypeAndSourceTypeAndSourceId(input.event(),sourceType,input.sourceId().toString()).isPresent();
        Request request=null;AdvanceBalance advance=null;Payslip payslip=null;Transaction transaction=null;BigDecimal amount;String code;
        if("REQUEST".equals(sourceType)) {
            request=sources.require(input.sourceId(),lock);amount=request.getApprovedAmount();code=request.getRequestCode();
            RequestType expected=switch(input.event()){case ADVANCE_DISBURSED->RequestType.ADVANCE;case EXPENSE_VERIFIED,EXPENSE_PAID->RequestType.EXPENSE;case REIMBURSE_SETTLED->RequestType.REIMBURSE;case DEPARTMENT_ALLOCATION->RequestType.DEPARTMENT_TOPUP;case PROJECT_ALLOCATION->RequestType.PROJECT_TOPUP;default->throw new BadRequestException("Sự kiện không hợp lệ cho request");};
            if(request.getType()!=expected)throw new BadRequestException("Loại yêu cầu không khớp sự kiện");
            if(request.getStatus()==RequestStatus.ACCOUNTANT_VERIFIED&&request.getType()!=RequestType.EXPENSE)throw new BadRequestException("Nguồn chưa hoàn tất");
            if(input.event()==AccountingJournalEvent.ADVANCE_DISBURSED)advance=sources.advanceForRequest(request.getId());
            if(input.event()==AccountingJournalEvent.REIMBURSE_SETTLED) {
                if(request.getAdvanceBalance()==null)throw new BadRequestException("Thiếu liên kết tạm ứng");
                advance=sources.advance(request.getAdvanceBalance().getId());
            }
            boolean needsCost=input.event()==AccountingJournalEvent.REIMBURSE_SETTLED
                    || (request.getType()==RequestType.EXPENSE && journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.EXPENSE_VERIFIED,"REQUEST",request.getId().toString()).isEmpty());
            if(!recorded&&needsCost&&!documents.hasAllocation(request.getId(),amount))validateDocuments(request,amount,input);
            if(request.getStatus()==RequestStatus.PAID&&request.getType()!=RequestType.REIMBURSE) {
                var fromType=request.getType()==RequestType.DEPARTMENT_TOPUP?WalletOwnerType.COMPANY_FUND:request.getType()==RequestType.PROJECT_TOPUP?WalletOwnerType.DEPARTMENT:WalletOwnerType.PROJECT;
                Long fromId=fromType==WalletOwnerType.COMPANY_FUND?1L:fromType==WalletOwnerType.DEPARTMENT?request.getProject().getDepartment().getId():request.getProject().getId();
                var toType=request.getType()==RequestType.DEPARTMENT_TOPUP?WalletOwnerType.DEPARTMENT:request.getType()==RequestType.PROJECT_TOPUP?WalletOwnerType.PROJECT:WalletOwnerType.USER;
                Long toId=toType==WalletOwnerType.DEPARTMENT?request.getRequester().getDepartment().getId():toType==WalletOwnerType.PROJECT?request.getProject().getId():request.getRequester().getId();
                var type=request.getType()==RequestType.DEPARTMENT_TOPUP?TransactionType.DEPT_QUOTA_ALLOCATION:request.getType()==RequestType.PROJECT_TOPUP?TransactionType.PROJECT_QUOTA_ALLOCATION:TransactionType.REQUEST_PAYMENT;
                transaction=wallets.requireTransfer(ReferenceType.REQUEST,request.getId(),type,amount,fromType,fromId,toType,toId);
            }
        } else if("PAYSLIP".equals(sourceType)) {
            payslip=payslips.findById(input.sourceId()).orElseThrow(()->new BadRequestException("Không có phiếu lương"));
            if(payslip.getStatus()!=PayslipStatus.PAID)throw new BadRequestException("Phiếu lương chưa chi hoặc đã đảo");
            if(payslip.getBaseSalary()==null||payslip.getBonus()==null||payslip.getAllowance()==null||payslip.getDeduction()==null||payslip.getAdvanceDeduct()==null||payslip.getFinalNetSalary()==null)
                throw new BadRequestException("Nguồn lương thiếu thành phần; cần xác minh, không mặc định bằng 0");
            if(input.payrollAllocations()!=null) {
                Set<Long> used=new HashSet<>();
                for(var allocation:input.payrollAllocations()) {
                    if(!used.add(allocation.advanceBalanceId())||!sources.advance(allocation.advanceBalanceId()).getUser().getId().equals(payslip.getUser().getId()))
                        throw new BadRequestException("Phân bổ tạm ứng bị trùng hoặc khác nhân viên trên phiếu lương");
                }
            }
            amount=payslip.getBaseSalary().add(payslip.getBonus()).add(payslip.getAllowance());code=payslip.getPayslipCode();
            if(amount.subtract(payslip.getDeduction()).subtract(payslip.getAdvanceDeduct()).compareTo(payslip.getFinalNetSalary())!=0)throw new BadRequestException("Phiếu lương không cân bằng");
            BigDecimal allocated=input.payrollAllocations()==null?BigDecimal.ZERO:input.payrollAllocations().stream().map(a->a.amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(!recorded&&allocated.compareTo(payslip.getAdvanceDeduct())!=0)throw new BadRequestException("Cần chứng cứ phân bổ khấu trừ tới từng tạm ứng; không suy diễn FIFO lịch sử");
            if(payslip.getFinalNetSalary().signum()>0)transaction=wallets.requireTransfer(ReferenceType.PAYSLIP,payslip.getId(),TransactionType.PAYSLIP_PAYMENT,payslip.getFinalNetSalary(),WalletOwnerType.COMPANY_FUND,1L,WalletOwnerType.USER,payslip.getUser().getId());
        } else {
            transaction=wallets.transaction(input.sourceId());amount=transaction.getAmount();code=transaction.getTransactionCode();
            if(transaction.getStatus()!=TransactionStatus.SUCCESS)throw new BadRequestException("Giao dịch chưa thành công");
            if(input.event()==AccountingJournalEvent.SYSTEM_TOPUP) {
                if(transaction.getType()!=TransactionType.SYSTEM_TOPUP||!bank.hasBookedMatch(transaction.getId()))throw new BadRequestException("Nạp quỹ cần sao kê BOOKED đã được đối chiếu");
            } else {
                if(transaction.getType()!=TransactionType.ADVANCE_RETURN||transaction.getReferenceType()!=ReferenceType.ADVANCE_BALANCE)throw new BadRequestException("Giao dịch không phải hoàn tạm ứng có tham chiếu");
                advance=sources.advance(transaction.getReferenceId());
                var original=advance.getAdvanceRequest();
                wallets.requireReturn(transaction.getId(),advance.getId(),advance.getUser().getId(),original.getProject().getId(),amount);
            }
        }
        return new Prepared(input,request,advance,payslip,transaction,amount,code,recorded);
    }
    private void validateDocuments(Request request,BigDecimal amount,OperationRequest input) {
        if(input.documents()==null||input.documents().isEmpty())throw new BadRequestException("Cần đối chiếu chứng từ và số tiền được sử dụng");
        if(input.documents().stream().map(d->d.allocatedAmount()).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(amount)!=0)throw new BadRequestException("Tổng phân bổ chứng từ không khớp số tiền nguồn");
        var attached=request.getAttachmentFiles().stream().map(f->f.getId()).toList();
        if(input.documents().stream().anyMatch(d->!attached.contains(d.fileId())||d.allocatedAmount().compareTo(d.totalAmount())>0))throw new BadRequestException("Chứng từ không thuộc yêu cầu hoặc vượt tổng tiền");
    }
}
