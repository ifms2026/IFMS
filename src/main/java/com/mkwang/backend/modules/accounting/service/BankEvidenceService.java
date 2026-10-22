package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.repository.CompanyFundRepository;
import com.mkwang.backend.modules.wallet.service.WalletFinancialEvidenceService;
import com.mkwang.backend.modules.spreadsheet.service.ExcelValues;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service @RequiredArgsConstructor
public class BankEvidenceService {
    private final BankCandidateQueryService candidateQueries;
    private final CompanyFundRepository funds;private final WalletFinancialEvidenceService movements;
    @PersistenceContext private EntityManager em;

    public record TopupEvidence(Long id, String bankReference, BigDecimal amount) {}

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyRole('CFO','ACCOUNTANT')")
    public com.mkwang.backend.common.dto.PageResponse<com.mkwang.backend.modules.accounting.dto.response.BankCandidateResponse> topupCandidates(String search,LocalDate from,LocalDate to,BigDecimal amount,int page,int limit) {
        return candidateQueries.find(true,account(),search,from,to,amount,page,limit);
    }

    @Transactional
    public TopupEvidence requireBookedTopupEvidence(Long lineId,BigDecimal amount) {
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
                SELECT s.bank_account,s.bank_reference,s.amount,s.direction,
                       CASE WHEN c.statement_line_id IS NOT NULL THEN 'BOOKED' ELSE s.state END,m.id
                FROM bank_statement_lines s
                LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                LEFT JOIN bank_statement_matches m ON m.statement_line_id=s.id
                WHERE s.id=:id FOR UPDATE OF s
                """).setParameter("id",lineId).getResultList();
        if(rows.isEmpty()) throw new BadRequestException("Cần chọn dòng sao kê đã xác nhận");
        Object[] row=rows.get(0);
        if(!account().equals(row[0])||!"IN".equals(row[3])||!"BOOKED".equals(row[4])||row[5]!=null
                ||((BigDecimal)row[2]).compareTo(amount)!=0) {
            throw new BadRequestException("Chứng từ nạp phải là dòng IN/BOOKED chưa dùng, đúng tài khoản và đúng số tiền");
        }
        return new TopupEvidence(lineId,(String)row[1],(BigDecimal)row[2]);
    }
    @Transactional(readOnly=true)
    public boolean hasBookedMatch(Long transactionId) {
        return ((Number)em.createNativeQuery("""
                SELECT COUNT(*) FROM bank_statement_matches m JOIN bank_statement_lines s ON s.id=m.statement_line_id
                LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                WHERE m.transaction_id=:id AND (s.state='BOOKED' OR c.statement_line_id IS NOT NULL)
                """).setParameter("id",transactionId).getSingleResult()).longValue()==1;
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyRole('CFO','ACCOUNTANT')")
    public com.mkwang.backend.common.dto.PageResponse<com.mkwang.backend.modules.accounting.dto.response.BankCandidateResponse> candidates(Long lineId,String search,LocalDate from,LocalDate to,int page,int limit) {
        var rows=em.createNativeQuery("SELECT bank_account,amount,direction FROM bank_statement_lines WHERE id=:id").setParameter("id",lineId).getResultList();
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không có dòng sao kê");
        Object[] row=(Object[])rows.getFirst();
        if(!account().equals(row[0])) throw new BadRequestException("Dòng sao kê không thuộc tài khoản đang đối soát");
        return candidateQueries.find(false,null,search,from,to,"IN".equals(row[2])?(BigDecimal)row[1]:((BigDecimal)row[1]).negate(),page,limit);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('CFO','ACCOUNTANT')")
    public void confirmMatch(Long lineId,Long transactionId,String reason) {
        var accounts=em.createNativeQuery("SELECT bank_account FROM bank_statement_lines WHERE id=:id").setParameter("id",lineId).getResultList();
        if(accounts.isEmpty())throw new ResourceNotFoundException("Không có dòng sao kê");
        if(!account().equals(accounts.getFirst()))throw new BadRequestException("Dòng sao kê không thuộc tài khoản đang đối soát");
        var principal=(com.mkwang.backend.modules.auth.security.UserDetailsAdapter)org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        match(lineId,transactionId,principal.getUser().getId(),reason);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('CFO','ACCOUNTANT')")
    public void configure(com.mkwang.backend.modules.accounting.dto.request.ConfigureBankAccountRequest input) {
        if(!"CORPORATE_OPERATING_POOL".equals(input.purpose())) throw new BadRequestException("Chỉ đối soát tài khoản tiền vận hành thuộc công ty trong IFMS");
        var fund=funds.findDefault().orElseThrow(()->new BadRequestException("Chưa có quỹ công ty"));
        em.createNativeQuery("SELECT id FROM company_funds WHERE id=:id FOR UPDATE").setParameter("id",fund.getId()).getSingleResult();
        if(fund.getBankAccount()!=null&&!fund.getBankAccount().equals(input.account())
                &&((Number)em.createNativeQuery("SELECT COUNT(*) FROM bank_statement_lines").getSingleResult()).longValue()>0) {
            throw new BadRequestException("Tài khoản đã có sao kê; cần chuyển đổi có đối chiếu thay vì ghi đè");
        }
        fund.setBankAccount(input.account().trim());fund.setBankName(input.bankName().trim());
        fund.setBankScopeConfirmed(true);fund.setBankScopeEvidence(input.evidenceRef().trim());
    }
    @Transactional(readOnly=true)
    public BigDecimal pendingSigned(java.time.Instant asOf) {
        @SuppressWarnings("unchecked") List<Number> ids=em.createNativeQuery("""
                SELECT m.transaction_id FROM bank_statement_matches m JOIN bank_statement_lines s ON s.id=m.statement_line_id
                WHERE s.bank_account=:account AND s.state='PENDING' AND s.booking_date<=:date
                  AND NOT EXISTS(SELECT 1 FROM bank_booking_confirmations c WHERE c.statement_line_id=s.id AND c.booking_date<=:date)
                """).setParameter("account",account()).setParameter("date",asOf.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()).getResultList();
        BigDecimal total=BigDecimal.ZERO;
        for(var id:ids) {
            var movement=movements.get(id.longValue());
            if(!movement.createdAt().isAfter(java.time.LocalDateTime.ofInstant(asOf,java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) total=total.add(movement.signedAmount());
        }
        return total;
    }
    @Transactional
    public void validateTopupReversal(Long line,BigDecimal amount) {
        if(line==null) throw new BadRequestException("Cần dòng sao kê xác nhận tiền đã trả ra ngân hàng");
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
                SELECT s.bank_account,s.direction,s.amount,CASE WHEN c.statement_line_id IS NOT NULL THEN 'BOOKED' ELSE s.state END,m.id FROM bank_statement_lines s
                LEFT JOIN bank_statement_matches m ON m.statement_line_id=s.id
                LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id WHERE s.id=:id FOR UPDATE OF s
                """).setParameter("id",line).getResultList();
        if(rows.isEmpty()) throw new BadRequestException("Không có chứng từ ngân hàng");
        var row=rows.get(0);
        if(!account().equals(row[0])||!"OUT".equals(row[1])||((BigDecimal)row[2]).compareTo(amount)!=0||!"BOOKED".equals(row[3])||row[4]!=null) {
            throw new BadRequestException("Cần sao kê OUT/BOOKED đúng tài khoản, đúng số tiền và chưa được sử dụng");
        }
    }
    public String account() {
        var fund=funds.findDefault().orElseThrow(()->new BadRequestException("Chưa khởi tạo quỹ công ty"));
        if(fund.getBankAccount()==null||fund.getBankAccount().isBlank()) throw new BadRequestException("Cần cấu hình tài khoản ngân hàng công ty trước");
        return fund.getBankAccount();
    }
    @Transactional(readOnly=true)
    public boolean exists(String account,String reference) {
        return ((Number)em.createNativeQuery("SELECT COUNT(*) FROM bank_statement_lines WHERE bank_account=:account AND bank_reference=:ref")
                .setParameter("account",account).setParameter("ref",reference).getSingleResult()).longValue()>0;
    }
    @Transactional(readOnly=true)
    public void validateImport(String account,String reference,BigDecimal amount,String direction,String state) {
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
                SELECT s.amount,s.direction,s.state,c.statement_line_id FROM bank_statement_lines s
                LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                WHERE s.bank_account=:account AND s.bank_reference=:ref
                """).setParameter("account",account).setParameter("ref",reference).getResultList();
        if(rows.isEmpty()) return;
        var row=rows.get(0);
        if(!"PENDING".equals(row[2])||row[3]!=null||!"BOOKED".equals(state)
                ||((BigDecimal)row[0]).compareTo(amount)!=0||!direction.equals(row[1])) {
            throw new BadRequestException("Sao kê đã nhập; chỉ cho phép xác nhận BOOKED cho dòng PENDING cùng số tiền/chiều tiền");
        }
    }
    @Transactional(readOnly=true)
    public void validateMatch(Long transactionId,BigDecimal amount,String direction) {
        var movement=movements.get(transactionId);
        if(movement.signedAmount().signum()==0||movement.signedAmount().abs().compareTo(amount)!=0
                ||("IN".equals(direction)!=(movement.signedAmount().signum()>0))) throw new BadRequestException("Giao dịch không khớp tiền/chiều tiền hoặc chỉ là phân bổ nội bộ");
        if(((Number)em.createNativeQuery("SELECT COUNT(*) FROM bank_statement_matches WHERE transaction_id=:id").setParameter("id",transactionId).getSingleResult()).longValue()>0) throw new BadRequestException("Giao dịch đã khớp dòng sao kê khác");
    }
    @Transactional
    public Long importLine(String account,String reference,LocalDate date,String direction,BigDecimal amount,String state,String description,Long actor,Long transaction) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key","IFMS_BANK:"+account+":"+reference).getSingleResult();
        validateImport(account,reference,amount,direction,state);
        if(exists(account,reference)) {
            Number existing=(Number)em.createNativeQuery("SELECT id FROM bank_statement_lines WHERE bank_account=:account AND bank_reference=:ref")
                    .setParameter("account",account).setParameter("ref",reference).getSingleResult();
            em.createNativeQuery("INSERT INTO bank_booking_confirmations(statement_line_id,booking_date,actor_id,evidence_ref) VALUES (:line,:date,:actor,:ref)")
                    .setParameter("line",existing.longValue()).setParameter("date",date).setParameter("actor",actor)
                    .setParameter("ref",description==null||description.isBlank()?reference:description).executeUpdate();
            if(transaction!=null) throw new BadRequestException("Khi xác nhận BOOKED, để trống transactionId; liên kết đối soát gốc được giữ nguyên");
            return existing.longValue();
        }
        Number id=(Number)em.createNativeQuery("""
                INSERT INTO bank_statement_lines(bank_account,bank_reference,booking_date,direction,amount,state,description,imported_by)
                VALUES (:account,:ref,:date,:direction,:amount,:state,:description,:actor) RETURNING id
                """).setParameter("account",account).setParameter("ref",reference).setParameter("date",date)
                .setParameter("direction",direction).setParameter("amount",amount).setParameter("state",state)
                .setParameter("description",description).setParameter("actor",actor).getSingleResult();
        if(transaction!=null) match(id.longValue(),transaction,actor,"Xác nhận đối chiếu khi nhập sao kê");
        return id.longValue();
    }
    @Transactional
    public void match(Long line,Long transaction,Long actor,String reason) {
        var rows=em.createNativeQuery("SELECT amount,direction FROM bank_statement_lines WHERE id=:id FOR UPDATE").setParameter("id",line).getResultList();
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không có dòng sao kê");
        Object[] source=(Object[])rows.getFirst();
        validateMatch(transaction,(BigDecimal)source[0],(String)source[1]);
        em.createNativeQuery("INSERT INTO bank_statement_matches(statement_line_id,transaction_id,matched_by,reason) VALUES (:line,:transaction,:actor,:reason)")
                .setParameter("line",line).setParameter("transaction",transaction).setParameter("actor",actor).setParameter("reason",reason).executeUpdate();
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyRole('CFO','ACCOUNTANT')")
    public List<Map<String,Object>> lines(LocalDate from,LocalDate to,int max) {
        if(from!=null && to!=null && from.isAfter(to)) throw new BadRequestException("Ngày bắt đầu phải trước ngày kết thúc");
        @SuppressWarnings("unchecked") List<Object[]> data=em.createNativeQuery("""
                SELECT s.id,s.bank_account,s.bank_reference,COALESCE(c.booking_date,s.booking_date),s.direction,s.amount,
                       CASE WHEN c.statement_line_id IS NOT NULL THEN 'BOOKED' ELSE s.state END,s.description,m.transaction_id
                FROM bank_statement_lines s LEFT JOIN bank_statement_matches m ON m.statement_line_id=s.id
                LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                WHERE s.bank_account=:account AND COALESCE(c.booking_date,s.booking_date)>=:from
                    AND COALESCE(c.booking_date,s.booking_date)<=:to ORDER BY COALESCE(c.booking_date,s.booking_date),s.id
                """).setParameter("account",account()).setParameter("from",from==null?LocalDate.of(1900,1,1):from)
                .setParameter("to",to==null?LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")):to).setMaxResults(max+1).getResultList();
        if(data.size()>max) throw new BadRequestException("Sao kê vượt giới hạn hiển thị; hãy thu hẹp khoảng ngày hoặc xuất Excel");
        return data.stream().map(row->ExcelValues.values("id",row[0],"bankAccount",row[1],"bankReference",row[2],"bookingDate",row[3].toString(),
                "direction",row[4],"amount",row[5],"state",row[6],"description",row[7],"transactionId",row[8],
                "matchingStatus",row[8]==null?"UNMATCHED":"PENDING".equals(row[6])?"IN_TRANSIT":"MATCHED")).toList();
    }
}
