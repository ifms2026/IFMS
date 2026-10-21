package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.dto.request.VerifyFinancialDocumentRequest;
import com.mkwang.backend.modules.request.service.RequestAccountingContextService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.util.*;
@Service @RequiredArgsConstructor
public class FinancialDocumentService {
    private final RequestAccountingContextService contexts;
    @PersistenceContext private EntityManager em;
    @Transactional(readOnly=true)
    public boolean hasAllocation(Long requestId,BigDecimal expected) {
        BigDecimal total=(BigDecimal)em.createNativeQuery("SELECT COALESCE(SUM(amount),0) FROM financial_document_allocations WHERE request_id=:id")
                .setParameter("id",requestId).getSingleResult();return total.compareTo(expected)==0;
    }
    @Transactional
    public void reverseAllocations(Long requestId,Long actorId,String reason) {
        @SuppressWarnings("unchecked")
        List<Number> documents=em.createNativeQuery("SELECT DISTINCT document_id FROM financial_document_allocations WHERE request_id=:id ORDER BY document_id")
                .setParameter("id",requestId).getResultList();
        for(var document:documents) em.createNativeQuery("SELECT id FROM financial_documents WHERE id=:id FOR UPDATE").setParameter("id",document.longValue()).getSingleResult();
        em.createNativeQuery("""
                INSERT INTO financial_document_allocations(document_id,request_id,amount,original_allocation_id,actor_id,reason)
                SELECT document_id,request_id,-amount,id,:actor,:reason FROM financial_document_allocations a
                WHERE request_id=:id AND original_allocation_id IS NULL
                  AND NOT EXISTS(SELECT 1 FROM financial_document_allocations r WHERE r.original_allocation_id=a.id)
                """).setParameter("id",requestId).setParameter("actor",actorId).setParameter("reason",reason).executeUpdate();
    }
    @Transactional
    public void allocate(Long requestId,BigDecimal amount,Long actorId,List<VerifyFinancialDocumentRequest> documents) {
        if(documents==null||documents.isEmpty()) throw new BadRequestException("Cần xác nhận thông tin và số tiền sử dụng trên chứng từ");
        if(documents.stream().map(VerifyFinancialDocumentRequest::allocatedAmount).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(amount)!=0) throw new BadRequestException("Tổng phân bổ chứng từ phải bằng số tiền được xác nhận");
        Set<Long> attachments=new HashSet<>();contexts.forRequest(requestId).documents().forEach(d->attachments.add(d.id()));
        var sorted=documents.stream().sorted(Comparator.comparing(this::key)).toList();
        Set<String> seen=new HashSet<>();
        for(var document:sorted) {
            if(!seen.add(key(document))) throw new BadRequestException("Chứng từ trùng trong lần xác nhận");
            if(!attachments.contains(document.fileId())) throw new BadRequestException("File không thuộc yêu cầu");
            if(document.documentDate().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) throw new BadRequestException("Ngày chứng từ không được ở tương lai");
            if(document.allocatedAmount().signum()<=0||document.allocatedAmount().compareTo(document.totalAmount())>0) throw new BadRequestException("Số tiền dùng trên chứng từ không hợp lệ");
            em.createNativeQuery("""
                    INSERT INTO financial_documents(document_key,issuer_code,document_number,document_date,total_amount,first_file_id,verified_by)
                    VALUES (:key,:issuer,:number,:date,:total,:file,:actor) ON CONFLICT(document_key) DO NOTHING
                    """).setParameter("key",key(document)).setParameter("issuer",normalize(document.issuerCode()))
                    .setParameter("number",normalize(document.documentNumber())).setParameter("date",document.documentDate())
                    .setParameter("total",document.totalAmount()).setParameter("file",document.fileId()).setParameter("actor",actorId).executeUpdate();
            Object[] stored=(Object[])em.createNativeQuery("SELECT id,total_amount FROM financial_documents WHERE document_key=:key FOR UPDATE")
                    .setParameter("key",key(document)).getSingleResult();
            Long id=((Number)stored[0]).longValue();
            if(((BigDecimal)stored[1]).compareTo(document.totalAmount())!=0) throw new BadRequestException("Cùng chứng từ nhưng tổng tiền khác lần xác nhận trước");
            BigDecimal used=(BigDecimal)em.createNativeQuery("SELECT COALESCE(SUM(amount),0) FROM financial_document_allocations WHERE document_id=:id")
                    .setParameter("id",id).getSingleResult();
            if(used.add(document.allocatedAmount()).compareTo(document.totalAmount())>0) throw new BadRequestException("Chứng từ đã được dùng; số phân bổ vượt tổng tiền");
            em.createNativeQuery("INSERT INTO financial_document_allocations(document_id,request_id,amount,actor_id) VALUES (:doc,:request,:amount,:actor)")
                    .setParameter("doc",id).setParameter("request",requestId).setParameter("amount",document.allocatedAmount())
                    .setParameter("actor",actorId).executeUpdate();
        }
    }
    private String normalize(String value){return java.text.Normalizer.normalize(value.trim(),java.text.Normalizer.Form.NFKC).replaceAll("\\s+","").toUpperCase(Locale.ROOT);}
    private String key(VerifyFinancialDocumentRequest document){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((normalize(document.issuerCode())+"|"+normalize(document.documentNumber())+"|"+document.documentDate()).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException error){throw new InternalSystemException("Cannot identify financial document");}
    }
}
