package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.dto.request.ReverseAccountingJournalRequest;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.AccountingJournalRepository;
import com.mkwang.backend.modules.accounting.mapper.AccountingJournalMapper;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.profile.service.ProfileService;
import com.mkwang.backend.modules.profile.dto.request.VerifyMyPinRequest;
import com.mkwang.backend.modules.request.service.RequestCorrectionService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountingCorrectionService {
    private final AccountingJournalRepository journals;private final AccountingJournalMapper mapper;
    private final RequestCorrectionService requests;private final ProfileService profiles;
    private final PayrollReversalService payroll;
    private final BankEvidenceService bank;
    private final com.mkwang.backend.modules.wallet.service.WalletService wallets;
    @PersistenceContext private EntityManager em;

    @Transactional(noRollbackFor={UnauthorizedException.class,LockedException.class})
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<AccountingJournalDetailResponse> reverse(Long id,ReverseAccountingJournalRequest input) {
        var actor=((UserDetailsAdapter)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        if(!profiles.verifyMyPin(actor.getId(),new VerifyMyPinRequest(input.pin())).isValid()) throw new UnauthorizedException("PIN không đúng");
        if(input.postingDate().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) throw new BadRequestException("Không ghi sổ ở tương lai");
        // Corrections may involve two journals and multiple original sources; serialize the orchestrator.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(89654322)").getSingleResult();
        var original=journals.findDetailById(id).orElseThrow(()->new ResourceNotFoundException("Journal not found"));
        var already=journals.findByOriginalJournalId(id);
        if(already.isPresent()) return List.of(mapper.toDetail(already.get()));
        if(!mapper.balanced(original)) throw new BadRequestException("Bút toán gốc chưa đủ căn cứ cân bằng");
        if(original.getEventType()==AccountingJournalEvent.REVERSAL || original.getEventType()==AccountingJournalEvent.OPENING_BALANCE) throw new BadRequestException("Không đảo trực tiếp bút toán đảo hoặc mở sổ");
        List<AccountingJournal> originals=new ArrayList<>();originals.add(original);
        if(original.getEventType()==AccountingJournalEvent.EXPENSE_PAID) {
            var verified=journals.findByRequestIdOrderByIdAsc(original.getRequestId()).stream()
                    .filter(j->j.getEventType()==AccountingJournalEvent.EXPENSE_VERIFIED).findFirst()
                    .orElseThrow(()->new BadRequestException("Thiếu bút toán xác nhận chi phí, cần đối chiếu nguồn"));
            if(journals.findByOriginalJournalId(verified.getId()).isPresent()) throw new BadRequestException("Chi phí gốc đã được đảo");
            originals.add(verified);
        }
        Long transactionId;
        if(original.getEventType()==AccountingJournalEvent.PAYROLL_SETTLEMENT) {
            var allocations=original.getLines().stream().filter(line->"ADVANCE_RECEIVABLE".equals(line.getAccountCode()) && line.getCreditAmount().signum()>0)
                    .map(line->new com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse(line.getAdvanceBalanceId(),line.getCreditAmount())).toList();
            transactionId=payroll.reverse(Long.valueOf(original.getSourceId()),original.getWalletTransactionId(),allocations,input.reason());
        } else if(original.getEventType()==AccountingJournalEvent.SYSTEM_TOPUP) {
            bank.validateTopupReversal(input.bankEvidenceId(),original.getTotalAmount());
            transactionId=wallets.reversal(original.getWalletTransactionId(),input.reason()).getId();
            bank.match(input.bankEvidenceId(),transactionId,actor.getId(),input.reason());
        } else if(original.getEventType()==AccountingJournalEvent.ADVANCE_RETURNED) {
            transactionId=requests.reverseReturn(original.getAdvanceBalanceId(),original.getWalletTransactionId(),original.getTotalAmount(),input.reason());
        } else if(original.getRequestId()!=null) {
            transactionId=requests.reverse(original.getRequestId(),original.getEventType().name(),original.getWalletTransactionId(),
                    original.getTotalAmount(),actor.getId(),input.reason());
        } else {
            throw new BadRequestException("Nguồn cần đối chiếu chuyên biệt trước khi đảo; không được chỉ đảo bút toán mà bỏ qua tiền/công nợ");
        }
        List<AccountingJournalDetailResponse> result=new ArrayList<>();
        for(var source:originals) {
            var reversal=AccountingJournal.builder().journalCode("JRN-R-"+UUID.randomUUID().toString().substring(0,20))
                    .eventType(AccountingJournalEvent.REVERSAL).postingDate(input.postingDate()).postingPeriod(YearMonth.from(input.postingDate()).toString())
                    .businessDate(input.postingDate()).description("Đảo toàn phần "+source.getJournalCode()+": "+input.reason())
                    .sourceType("JOURNAL_REVERSAL").sourceId(source.getId().toString()).originalJournalId(source.getId())
                    .requestId(source.getRequestId()).advanceBalanceId(source.getAdvanceBalanceId()).employeeId(source.getEmployeeId())
                    .employeeName(source.getEmployeeName()).projectId(source.getProjectId()).projectName(source.getProjectName())
                    .departmentId(source.getDepartmentId()).departmentName(source.getDepartmentName())
                    .phaseId(source.getPhaseId()).phaseName(source.getPhaseName()).categoryId(source.getCategoryId()).categoryName(source.getCategoryName())
                    .sourceSnapshot(source.getSourceSnapshot()).walletTransactionId(source.getWalletTransactionId()==null?null:transactionId)
                    .totalAmount(source.getTotalAmount()).createdByUserId(actor.getId()).createdByName(actor.getFullName()).createdAt(Instant.now()).build();
            for(var line:source.getLines()) reversal.getLines().add(AccountingJournalLine.builder().journal(reversal).lineNumber(line.getLineNumber())
                    .accountCode(line.getAccountCode()).accountName(line.getAccountName()).debitAmount(line.getCreditAmount()).creditAmount(line.getDebitAmount())
                    .effectDescription("Đảo toàn phần bút toán "+source.getJournalCode()).advanceBalanceId(line.getAdvanceBalanceId())
                    .requestId(line.getRequestId()).employeeId(line.getEmployeeId()).projectId(line.getProjectId()).projectName(line.getProjectName())
                    .departmentId(line.getDepartmentId()).phaseId(line.getPhaseId()).phaseName(line.getPhaseName())
                    .categoryId(line.getCategoryId()).categoryName(line.getCategoryName()).build());
            result.add(mapper.toDetail(journals.save(reversal)));
        }
        return result;
    }
}
