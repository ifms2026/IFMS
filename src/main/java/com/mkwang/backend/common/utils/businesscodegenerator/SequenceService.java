package com.mkwang.backend.common.utils.businesscodegenerator;

import com.mkwang.backend.common.exception.InternalSystemException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service lấy nextval() từ PostgreSQL Sequences.
 * <p>
 * Pre-cache SQL strings per {@link BusinessCodeType} → không tạo String mới mỗi lần gọi.
 * Regex sanitize được pre-compile.
 */
@Service
public class SequenceService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Pre-compiled regex — chỉ cho phép alphanumeric + underscore */
    private static final Pattern SAFE_SEQ_NAME = Pattern.compile("[^a-zA-Z0-9_]");

    /**
     * Pre-built SQL strings cho mỗi BusinessCodeType có sequence.
     * Tránh String concat mỗi lần gọi: "SELECT nextval('" + name + "')"
     */
    private static final Map<BusinessCodeType, String> SQL_CACHE;

    static {
        SQL_CACHE = new EnumMap<>(BusinessCodeType.class);
        for (BusinessCodeType type : BusinessCodeType.values()) {
            if (type.needsSequence()) {
                SQL_CACHE.put(type, "SELECT nextval('" + type.getSequenceName() + "')");
            }
        }
    }

    /**
     * Lấy nextval cho một {@link BusinessCodeType}.
     * SQL đã được cache sẵn → zero allocation per call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long getNextValue(BusinessCodeType codeType) {
        String sql = SQL_CACHE.get(codeType);
        if (sql == null) {
            throw new InternalSystemException(
                    "BusinessCodeType." + codeType.name() + " does not use a PostgreSQL sequence");
        }
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    /**
     * Lấy nextval bằng tên sequence (dùng cho trường hợp dynamic ngoài enum).
     * Sanitize input để chống SQL injection.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long getNextValue(String sequenceName) {
        if (sequenceName == null || sequenceName.isBlank()) {
            throw new InternalSystemException("Sequence name must not be null or blank");
        }
        String sanitized = SAFE_SEQ_NAME.matcher(sequenceName).replaceAll("");
        if (sanitized.isEmpty()) {
            throw new InternalSystemException("Sequence name contains no valid characters: " + sequenceName);
        }
        String sql = "SELECT nextval('" + sanitized + "')";
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
