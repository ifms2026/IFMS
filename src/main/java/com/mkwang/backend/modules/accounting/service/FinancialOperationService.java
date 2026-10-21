package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
@Service @RequiredArgsConstructor
public class FinancialOperationService {
    @PersistenceContext private EntityManager em;private final ObjectMapper json;
    @Transactional
    public <T>T execute(Long actor,String operation,String key,Object payload,Class<T> type,Supplier<T> action) {
        if(key==null||key.isBlank()||key.length()>80) throw new BadRequestException("Cần mã thao tác để chống hoàn tiền lặp khi gửi lại");
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(payload)));
            em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                    .setParameter("key","IFMS_OPERATION:"+actor+":"+operation+":"+key).getSingleResult();
            @SuppressWarnings("unchecked") List<Object[]> existing=em.createNativeQuery("""
                    SELECT payload_hash,response FROM financial_operation_receipts
                    WHERE actor_id=:actor AND operation=:operation AND idempotency_key=:key
                    """).setParameter("actor",actor).setParameter("operation",operation).setParameter("key",key).getResultList();
            if(!existing.isEmpty()) {
                if(!hash.equals(existing.get(0)[0])) throw new ConflictException("Mã thao tác đã dùng cho nội dung khác");
                return json.readValue((String)existing.get(0)[1],type);
            }
            T result=action.get();
            em.createNativeQuery("INSERT INTO financial_operation_receipts(actor_id,operation,idempotency_key,payload_hash,response) VALUES (:actor,:operation,:key,:hash,:response)")
                    .setParameter("actor",actor).setParameter("operation",operation).setParameter("key",key)
                    .setParameter("hash",hash).setParameter("response",json.writeValueAsString(result)).executeUpdate();
            return result;
        } catch(com.fasterxml.jackson.core.JsonProcessingException|NoSuchAlgorithmException error) {
            throw new InternalSystemException("Không lưu được biên nhận thao tác tài chính");
        }
    }
}
