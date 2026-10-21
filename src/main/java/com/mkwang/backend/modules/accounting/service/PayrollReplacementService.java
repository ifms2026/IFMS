package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.dto.request.*;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.request.service.RequestService;
import com.mkwang.backend.modules.wallet.service.WalletService;
import com.mkwang.backend.modules.wallet.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
@Service @RequiredArgsConstructor
public class PayrollReplacementService {
    private final PayslipRepository slips;private final AccountingJournalRepository journals;
    private final AccountingCorrectionService corrections;private final AccountingJournalService accounting;
    private final RequestService requests;private final WalletService wallets;
    @Transactional(noRollbackFor={UnauthorizedException.class,LockedException.class})
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public Long replace(Long journalId,ReplacePayslipRequest input) {
        var original=journals.findDetailById(journalId).orElseThrow(()->new ResourceNotFoundException("Journal not found"));
        if(original.getEventType()!=AccountingJournalEvent.PAYROLL_SETTLEMENT) throw new BadRequestException("Nguồn không phải phiếu lương");
        Long originalId=Long.valueOf(original.getSourceId());
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        corrections.reverse(journalId,new ReverseAccountingJournalRequest(input.reason()+" · "+input.evidenceRef(),input.pin(),today,null));
        var existing=slips.findByReplacesPayslipId(originalId);
        if(existing.isPresent()) {
            var slip=existing.get();
            if(slip.getBaseSalary().compareTo(input.baseSalary())!=0 || slip.getBonus().compareTo(input.bonus())!=0
                    || slip.getAllowance().compareTo(input.allowance())!=0 || slip.getDeduction().compareTo(input.deduction())!=0) throw new BadRequestException("Phiếu thay thế đã được ghi với số liệu khác");
            return slip.getId();
        }
        var source=slips.findById(originalId).orElseThrow(()->new ResourceNotFoundException("Payslip not found"));
        requests.lockEmployeeFinancialScope(source.getUser().getId());
        BigDecimal earnings=input.baseSalary().add(input.bonus()).add(input.allowance()).subtract(input.deduction());
        if(earnings.signum()<0) throw new BadRequestException("Khấu trừ vượt thu nhập");
        BigDecimal offset=requests.getTotalOutstandingDebt(source.getUser().getId()).min(earnings.multiply(new BigDecimal("0.5")).setScale(2,java.math.RoundingMode.HALF_UP));
        var slip=Payslip.builder().period(source.getPeriod()).user(source.getUser()).replacesPayslipId(source.getId())
                .payslipCode("PSL-C-"+source.getId()+"-"+java.util.UUID.randomUUID().toString().substring(0,8))
                .baseSalary(input.baseSalary()).bonus(input.bonus()).allowance(input.allowance()).deduction(input.deduction())
                .advanceDeduct(offset).finalNetSalary(earnings.subtract(offset)).status(PayslipStatus.PAID).paymentDate(LocalDateTime.now()).build();
        slips.save(slip);
        var allocations=offset.signum()>0?requests.applyPayrollDeduction(source.getUser().getId(),offset):List.<com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse>of();
        var transaction=slip.getFinalNetSalary().signum()>0?wallets.transfer(WalletOwnerType.COMPANY_FUND,1L,WalletOwnerType.USER,source.getUser().getId(),
                slip.getFinalNetSalary(),TransactionType.PAYSLIP_PAYMENT,ReferenceType.PAYSLIP,slip.getId(),"Điều chỉnh "+source.getPayslipCode()+" · "+input.evidenceRef()):null;
        accounting.recordPayrollSettlement(slip,transaction,allocations);return slip.getId();
    }
}
