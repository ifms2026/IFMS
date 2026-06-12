package com.mkwang.backend.modules.audit.context;

import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * AuditContextFilter — populate AuditContextHolder từ Spring Security sau mỗi
 * request.
 * <p>
 * Thứ tự filter thực thi:
 * 
 * <pre>
 *   [order = -100] Spring Security Chain  ← JwtAuthenticationFilter set SecurityContext ở đây
 *   [order = -99]  AuditContextFilter     ← ĐỌC SecurityContext → set traceId + actorId
 *   [any order]    DispatcherServlet + Controllers
 * </pre>
 * 
 * CRITICAL: Phải chạy SAU Security Chain (order > -100),
 * nếu không SecurityContextHolder chưa được JwtFilter populate.
 * <p>
 * Order = {@code SecurityProperties.DEFAULT_FILTER_ORDER + 1} = -99
 */
@Slf4j
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class AuditContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Generate UUID mới cho mỗi request — gom nhóm tất cả audit log của request này
            String traceId = UUID.randomUUID().toString();

            // Đọc actorId từ SecurityContext (đã được JwtAuthenticationFilter populate)
            Long actorId = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof UserDetailsAdapter adapter) {
                // ✅ Authenticated via JWT — principal là UserDetailsAdapter
                actorId = adapter.getUser().getId();
                log.debug("[AuditContextFilter] ✅ Authenticated: actorId={}, {} {}",
                        actorId, request.getMethod(), request.getServletPath());
            } else {
                // ℹ️ Public/anonymous request — actorId = null là ĐÚNG
                // Ví dụ: login, refresh-token, swagger, unauthenticated request
                // auth.principal sẽ là "anonymousUser" (AnonymousAuthenticationToken) hoặc null
                log.debug("[AuditContextFilter] ℹ️ Public/anonymous: principal={}, {} {}",
                        auth != null ? auth.getPrincipal().getClass().getSimpleName() : "null",
                        request.getMethod(), request.getServletPath());
            }

            AuditContextHolder.set(traceId, actorId);
            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: clear() trong finally — tránh memory leak trong Tomcat thread pool
            AuditContextHolder.clear();
        }
    }
}
