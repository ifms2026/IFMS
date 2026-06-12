package com.mkwang.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.modules.config.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Map;

/**
 * Chặn tất cả API requests khi SYSTEM_MAINTENANCE_MODE = true.
 * Exempt: /auth/**, /health, /actuator/**
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    private static final String MAINTENANCE_KEY = "SYSTEM_MAINTENANCE_MODE";

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        boolean maintenanceOn = systemConfigService.getAsBoolean(MAINTENANCE_KEY, false);

        if (!maintenanceOn) {
            return true;
        }

        String path = request.getRequestURI();

        // Exempt auth and health endpoints so admins can still log in
        if (path.contains("/auth/") || path.endsWith("/health") || path.contains("/actuator")) {
            return true;
        }

        log.info("[Maintenance] Blocked request: {} {}", request.getMethod(), path);

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
            "success", false,
            "message", "Hệ thống đang trong chế độ bảo trì. Vui lòng thử lại sau.",
            "timestamp", Instant.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
