package com.mkwang.backend.modules.request.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.request.repository.*;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.project.service.*;
import com.mkwang.backend.modules.wallet.service.WalletService;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.accounting.service.FinancialDocumentService;
import com.mkwang.backend.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class RequestCorrectionService {
    private final RequestRepository requests;private final AdvanceBalanceRepository advances;
    private final ProjectQueryService projects;private final ProjectAccountingAdjustmentService adjustments;
    private final FinancialDocumentService documents;private final WalletService wallets;private final UserService users;
    @Transactional
    public void undoPayrollOffsets(Long employeeId,java.util.List<com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse> allocations) {
        users.lockFinancialScope(employeeId);
        var balances=allocations.stream().map(a->advances.findById(a.advanceBalanceId()).orElseThrow(()->new BadRequestException("Thiếu tạm ứng nguồn"))).toList();
        balances.stream().map(b->b.getAdvanceRequest().getProject().getId()).distinct().sorted().forEach(projects::lockFinancialProject);
        for(var allocation:allocations.stream().sorted(java.util.Comparator.comparing(com.mkwang.backend.modules.request.dto.response.AdvanceSettlementAllocationResponse::advanceBalanceId)).toList()) {
            var balance=advances.findByIdForUpdate(allocation.advanceBalanceId()).orElseThrow(()->new BadRequestException("Thiếu tạm ứng nguồn"));
            if(!employeeId.equals(balance.getUser().getId())) throw new BadRequestException("Tạm ứng không thuộc nhân viên trên phiếu lương");
            var request=balance.getAdvanceRequest();
            adjustments.checkReopenAdvance(request.getProject().getId(),request.getPhase().getId(),request.getCategory().getId(),allocation.amount());
            balance.reopenPayrollOffset(allocation.amount());
        }
    }
    /** Called only by the authorized correction orchestrator, in its transaction. */
    @Transactional
    public Long reverse(Long requestId,String event,Long originalTransactionId,BigDecimal amount,Long actorId,String reason) {
        var request=requests.findByIdForUpdate(requestId).orElseThrow(()->new ResourceNotFoundException("Request not found"));
        if(request.getStatus()!=RequestStatus.PAID&&request.getStatus()!=RequestStatus.ACCOUNTANT_VERIFIED) throw new BadRequestException("Nguồn chưa ghi nhận hoặc đã được đảo");
        users.lockFinancialScope(request.getRequester().getId());
        if(request.getApprovedAmount()==null||request.getApprovedAmount().compareTo(amount)!=0) throw new BadRequestException("Số tiền nguồn không khớp bút toán");
        if(request.getProject()!=null) projects.lockFinancialProject(request.getProject().getId());
        Long transactionId=null;
        switch(event) {
            case "EXPENSE_VERIFIED" -> {
                if(request.getStatus()!=RequestStatus.ACCOUNTANT_VERIFIED) throw new BadRequestException("Khoản hoàn chi đã thanh toán; cần đảo từ bút toán thanh toán để xử lý cả chi phí và tiền");
                wallets.unlockFunds(WalletOwnerType.PROJECT,request.getProject().getId(),request.releaseReservation());
                reverseCost(request,amount,actorId,reason);
            }
            case "EXPENSE_PAID" -> {
                transactionId=wallets.reversal(originalTransactionId,reason).getId();
                reverseCost(request,amount,actorId,reason);
            }
            case "ADVANCE_DISBURSED" -> {
                var balance=advances.findByAdvanceRequest_Id(requestId).orElseThrow(()->new BadRequestException("Không tìm thấy khoản tạm ứng"));
                balance=advances.findByIdForUpdate(balance.getId()).orElseThrow(()->new BadRequestException("Không tìm thấy khoản tạm ứng"));
                if(balance.getRemainingAmount().compareTo(balance.getOriginalAmount())!=0 || balance.getReimburseRequests().stream().anyMatch(r -> r.getStatus() != RequestStatus.CANCELLED && r.getStatus() != RequestStatus.REJECTED && r.getStatus() != RequestStatus.REVERSED)) throw new BadRequestException("Tạm ứng đã có nghiệp vụ phụ thuộc; xử lý các quyết toán/hoàn trả/khấu trừ trước");
                transactionId=wallets.reversal(originalTransactionId,reason).getId();
                balance.returnCash(amount);
            }
            case "REIMBURSE_SETTLED" -> {
                var balance=advances.findByIdForUpdate(request.getAdvanceBalance().getId()).orElseThrow(()->new BadRequestException("Không tìm thấy tạm ứng"));
                reverseCost(request,amount,actorId,reason);
                balance.reopenReimbursement(amount);
            }
            case "PROJECT_ALLOCATION" -> {
                adjustments.reverseFunding(request.getProject().getId(),amount);
                transactionId=wallets.reversal(originalTransactionId,reason).getId();
            }
            case "DEPARTMENT_ALLOCATION" -> transactionId=wallets.reversal(originalTransactionId,reason).getId();
            default -> throw new BadRequestException("Nguồn yêu cầu không hỗ trợ loại đảo này");
        }
        request.setStatus(RequestStatus.REVERSED);
        request.getHistories().add(RequestHistory.builder().request(request).actor(users.getUserById(actorId))
                .action(RequestAction.REVERSE).statusAfterAction(RequestStatus.REVERSED).comment(reason).build());
        return transactionId;
    }
    private void reverseCost(Request request,BigDecimal amount,Long actor,String reason) {
        adjustments.reverseCost(request.getProject().getId(),request.getPhase().getId(),request.getCategory().getId(),amount);
        documents.reverseAllocations(request.getId(),actor,reason);
    }
    @Transactional
    public Long reverseReturn(Long advanceId,Long transactionId,BigDecimal amount,String reason) {
        var observed=advances.findById(advanceId).orElseThrow(()->new BadRequestException("Không tìm thấy tạm ứng"));
        users.lockFinancialScope(observed.getUser().getId());
        var request=observed.getAdvanceRequest();
        projects.lockFinancialProject(request.getProject().getId());
        var balance=advances.findByIdForUpdate(advanceId).orElseThrow(()->new BadRequestException("Không tìm thấy tạm ứng"));
        adjustments.checkReopenAdvance(request.getProject().getId(),request.getPhase().getId(),request.getCategory().getId(),amount);
        Long refund=wallets.reversal(transactionId,reason).getId();balance.reopenCashReturn(amount);return refund;
    }
}
