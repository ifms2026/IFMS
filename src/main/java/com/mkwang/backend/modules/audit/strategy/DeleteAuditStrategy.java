package com.mkwang.backend.modules.audit.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.modules.audit.context.AuditContextHolder;
import com.mkwang.backend.modules.audit.dto.AuditMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteAuditStrategy implements AuditExtractionStrategy {

    private final ObjectMapper objectMapper;

    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "pin", "pinCode", "cvv", "secret", "hashValue");

    @Override
    public AuditMessageDTO extract(String entityName, String entityId, Long actorId,
                                   String[] propertyNames, Object[] oldState, Object[] newState) {
        Map<String, Object> oldMap = new LinkedHashMap<>();
        for (int i = 0; i < propertyNames.length; i++) {
            String propName = propertyNames[i];
            Object value = oldState != null ? oldState[i] : null;
            oldMap.put(propName, maskIfSensitive(propName, value));
        }

        return new AuditMessageDTO(
                AuditContextHolder.getTraceId(),
                "DELETE",
                entityName,
                entityId,
                actorId,
                toJson(oldMap),
                null
        );
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
            log.warn("[DeleteAuditStrategy] JSON serialize failed: {}", e.getMessage());
            return null;
        }
    }
}
