package com.mkwang.backend.modules.audit.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.modules.audit.context.AuditContextHolder;
import com.mkwang.backend.modules.audit.dto.AuditMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UpdateAuditStrategy — bóc tách ONLY dirty fields khi UPDATE.
 * BigDecimal dùng compareTo() thay vì equals() để tránh phân biệt scale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateAuditStrategy implements AuditExtractionStrategy {

    private final ObjectMapper objectMapper;

    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "pin", "pinCode", "cvv", "secret", "hashValue");

    @Override
    public AuditMessageDTO extract(String entityName, String entityId, Long actorId,
                                   String[] propertyNames, Object[] oldState, Object[] newState) {

        Map<String, Object> oldMap = new LinkedHashMap<>();
        Map<String, Object> newMap = new LinkedHashMap<>();

        // Duyệt qua từng property — chỉ lấy field thực sự thay đổi
        for (int i = 0; i < propertyNames.length; i++) {
            Object oldVal = (oldState != null) ? oldState[i] : null;
            Object newVal = (newState != null) ? newState[i] : null;
            if (!isDirty(oldVal, newVal)) continue;
            String propName = propertyNames[i];
            oldMap.put(propName, maskIfSensitive(propName, oldVal));
            newMap.put(propName, maskIfSensitive(propName, newVal));
        }

        if (oldMap.isEmpty()) {
            // Không có dirty field — touch-only update, bỏ qua
            return new AuditMessageDTO(AuditContextHolder.getTraceId(), "UPDATE",
                    entityName, entityId, actorId, null, null);
        }

        return new AuditMessageDTO(
                AuditContextHolder.getTraceId(),
                "UPDATE",
                entityName,
                entityId,
                actorId,
                toJson(oldMap),
                toJson(newMap)
        );
    }

    /**
     * BigDecimal: dùng compareTo() — "1.0".equals("1.00") = false nhưng compareTo = 0.
     * Collection: bỏ qua (Hibernate proxy, không so sánh được reliably).
     */
    private boolean isDirty(Object oldVal, Object newVal) {
        if (oldVal == null && newVal == null) return false;
        if (oldVal == null || newVal == null) return true;
        if (oldVal instanceof BigDecimal o && newVal instanceof BigDecimal n)
            return o.compareTo(n) != 0;
        if (oldVal instanceof java.util.Collection || newVal instanceof java.util.Collection)
            return false;
        return !Objects.equals(oldVal, newVal);
    }

    private Object maskIfSensitive(String propName, Object value) {
        return SENSITIVE_FIELDS.contains(propName) ? "***" : normalizeValue(value);
    }

    private Object normalizeValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof String
                || value instanceof Boolean || value instanceof Enum) return value;
        if (value instanceof java.time.temporal.Temporal) return value.toString();
        if (value instanceof java.util.Collection) return null;
        try { return value.toString(); } catch (Exception e) { return null; }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("[UpdateAuditStrategy] JSON serialize failed: {}", e.getMessage());
            return null;
        }
    }
}
