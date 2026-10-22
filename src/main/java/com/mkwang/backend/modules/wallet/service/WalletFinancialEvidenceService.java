package com.mkwang.backend.modules.wallet.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.wallet.repository.*;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.wallet.dto.response.CorporateMovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor
public class WalletFinancialEvidenceService {
    private final TransactionRepository transactions;private final LedgerEntryRepository entries;
    private final WalletRepository wallets;
    @Transactional
    public java.math.BigDecimal lockBalance(WalletOwnerType type,Long id) {
        if(!Set.of(WalletOwnerType.COMPANY_FUND,WalletOwnerType.DEPARTMENT,WalletOwnerType.PROJECT).contains(type))
            throw new BadRequestException("Chỉ mở sổ cho tiền thuộc công ty; ví cá nhân và ví kiểm soát theo dõi riêng");
        return wallets.findByOwnerTypeAndOwnerIdForUpdate(type,id).orElseThrow(()->new ResourceNotFoundException("Không có ví nguồn")).getBalance();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> openingScopes() {
        return wallets.findAll().stream().filter(w->Set.of(WalletOwnerType.COMPANY_FUND,WalletOwnerType.DEPARTMENT,WalletOwnerType.PROJECT).contains(w.getOwnerType()))
                .map(w->com.mkwang.backend.modules.spreadsheet.service.ExcelValues.values("kind",w.getOwnerType().name(),"id",w.getOwnerId(),"balance",w.getBalance())).toList();
    }
    @Transactional(readOnly=true)
    public Transaction requireTransfer(ReferenceType reference,Long source,TransactionType type,BigDecimal amount,
            WalletOwnerType fromType,Long fromId,WalletOwnerType toType,Long toId) {
        var matches=transactions.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(reference,source).stream()
                .filter(t->t.getType()==type&&t.getStatus()==TransactionStatus.SUCCESS&&t.getAmount().compareTo(amount)==0).toList();
        if(matches.size()!=1) throw new BadRequestException("Không xác định duy nhất giao dịch chuyển tiền đúng số tiền");
        var transaction=matches.get(0);var movements=entries.findByTransactionId(transaction.getId());
        boolean sourceMatch=movements.stream().anyMatch(e->e.getDirection()==TransactionDirection.DEBIT&&e.getWallet().getOwnerType()==fromType&&e.getWallet().getOwnerId().equals(fromId)&&e.getAmount().compareTo(amount)==0);
        boolean targetMatch=movements.stream().anyMatch(e->e.getDirection()==TransactionDirection.CREDIT&&e.getWallet().getOwnerType()==toType&&e.getWallet().getOwnerId().equals(toId)&&e.getAmount().compareTo(amount)==0);
        if(movements.size()!=2||!sourceMatch||!targetMatch)throw new BadRequestException("Biến động ví không khớp nguồn/đích của nghiệp vụ");
        return transaction;
    }
    @Transactional(readOnly=true)
    public void requireReturn(Long transactionId,Long advanceId,Long employeeId,Long projectId,BigDecimal amount) {
        var transaction=transaction(transactionId);var movements=entries.findByTransactionId(transactionId);
        if(transaction.getType()!=TransactionType.ADVANCE_RETURN||transaction.getStatus()!=TransactionStatus.SUCCESS||transaction.getReferenceType()!=ReferenceType.ADVANCE_BALANCE||!advanceId.equals(transaction.getReferenceId())||movements.size()!=2
            || movements.stream().noneMatch(e->e.getDirection()==TransactionDirection.DEBIT&&e.getWallet().getOwnerType()==WalletOwnerType.USER&&employeeId.equals(e.getWallet().getOwnerId())&&amount.compareTo(e.getAmount())==0)
            || movements.stream().noneMatch(e->e.getDirection()==TransactionDirection.CREDIT&&e.getWallet().getOwnerType()==WalletOwnerType.PROJECT&&projectId.equals(e.getWallet().getOwnerId())&&amount.compareTo(e.getAmount())==0))
            throw new BadRequestException("Hoàn tiền không khớp ví nguồn/đích của tạm ứng");
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> historicalTransactions(String search,int max) {
        var page=transactions.findHistoricalAccountingSources(search,org.springframework.data.domain.PageRequest.of(0,max+1));
        if(page.getTotalElements()>max)throw new BadRequestException("Có quá nhiều giao dịch trùng mã tìm kiếm; nhập mã cụ thể hơn");
        return page.stream()
            .map(t->com.mkwang.backend.modules.spreadsheet.service.ExcelValues.values("kind","TRANSACTION","sourceId",t.getId(),"code",t.getTransactionCode(),"event",t.getType()==TransactionType.SYSTEM_TOPUP?"SYSTEM_TOPUP":"ADVANCE_RETURNED","amount",t.getAmount(),"businessDate",t.getCreatedAt().toLocalDate().toString(),"documents",List.of())).toList();
    }
    @Transactional(readOnly=true)
    public Transaction transaction(Long id){return transactions.findById(id).orElseThrow(()->new ResourceNotFoundException("Transaction not found"));}
    @Transactional(readOnly=true)
    public BigDecimal corporateBalanceAt(java.time.Instant asOf) {
        BigDecimal current=wallets.sumBalancesByType(WalletOwnerType.COMPANY_FUND)
                .add(wallets.sumBalancesByType(WalletOwnerType.DEPARTMENT))
                .add(wallets.sumBalancesByType(WalletOwnerType.PROJECT));
        return current.subtract(entries.sumCorporateMovementAfter(java.time.LocalDateTime.ofInstant(asOf,java.time.ZoneId.of("Asia/Ho_Chi_Minh"))));
    }
    @Transactional(readOnly=true)
    public CorporateMovementResponse get(Long id) {
        var transaction=transactions.findById(id).orElseThrow(()->new ResourceNotFoundException("Transaction not found"));
        if(transaction.getStatus()!=TransactionStatus.SUCCESS) throw new BadRequestException("Giao dịch chưa thành công");
        BigDecimal signed=entries.findByTransactionId(id).stream()
                .filter(line->Set.of(WalletOwnerType.COMPANY_FUND,WalletOwnerType.DEPARTMENT,WalletOwnerType.PROJECT).contains(line.getWallet().getOwnerType()))
                .map(line->line.getDirection()==TransactionDirection.CREDIT?line.getAmount():line.getAmount().negate())
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        return new CorporateMovementResponse(id,transaction.getTransactionCode(),transaction.getPaymentRef(),transaction.getCreatedAt(),transaction.getType().name(),signed);
    }
}
