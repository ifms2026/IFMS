package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse;
import com.mkwang.backend.modules.request.service.RequestCorrectionService;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
@Service @RequiredArgsConstructor
public class PayrollReversalService {
    private final PayrollPeriodRepository periods;private final PayslipRepository slips;
    private final RequestCorrectionService requests;private final WalletService wallets;
    @Transactional
    public Long reverse(Long id,Long transaction,List<AdvanceSettlementAllocationResponse> offsets,String reason) {
        periods.lockPayrollRun();
        var observed=slips.findById(id).orElseThrow(()->new ResourceNotFoundException("Payslip not found"));
        periods.findByIdForUpdate(observed.getPeriod().getId()).orElseThrow(()->new ResourceNotFoundException("Period not found"));
        var payslip=slips.findById(id).orElseThrow(()->new ResourceNotFoundException("Payslip not found"));
        if(payslip.getStatus()!=PayslipStatus.PAID) throw new BadRequestException("Phiếu lương chưa chi hoặc đã đảo");
        BigDecimal total=offsets.stream().map(AdvanceSettlementAllocationResponse::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(payslip.getAdvanceDeduct())!=0) throw new BadRequestException("Thiếu phân bổ khấu trừ gốc, không được suy diễn FIFO lịch sử");
        requests.undoPayrollOffsets(payslip.getUser().getId(),offsets);
        Long reversal=transaction==null?null:wallets.reversal(transaction,reason).getId();
        payslip.setStatus(PayslipStatus.REVERSED);return reversal;
    }
}
