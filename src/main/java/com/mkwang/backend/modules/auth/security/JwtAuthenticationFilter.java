package com.mkwang.backend.modules.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — verify token + enforce single-session (token version).
 * <p>
 * Flow bổ sung cho single-session:
 * <pre>
 *   1. Extract "ver" claim từ token
 *   2. Lấy version hiện tại từ Redis (fallback DB)
 *   3. Nếu ver_token < ver_hệ_thống → 401 "Tài khoản đã đăng nhập ở thiết bị khác"
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip filter for public auth endpoints only
        String path = request.getServletPath();
        if (path.equals("/auth/login")
                || path.equals("/auth/refresh-token")
                || path.equals("/auth/forgot-password")
                || path.equals("/auth/verify-password-reset")
                || path.equals("/auth/reset-password")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // Check account status
                if (!userDetails.isEnabled()) {
                    authenticationEntryPoint.commence(request, response,
                            new DisabledException("Account is disabled. Contact administrator."));
                    return;
                }
                if (!userDetails.isAccountNonLocked()) {
                    authenticationEntryPoint.commence(request, response,
                            new LockedException("Account is locked. Contact administrator."));
                    return;
                }

                // Validate token signature + expiry
                if (!jwtService.isTokenValid(jwt, userDetails)) {
                    authenticationEntryPoint.commence(request, response,
                            new BadCredentialsException("Invalid or expired token."));
                    return;
                }

                // ── Single-session check: version trong token vs Redis/DB ──
                UserDetailsAdapter adapter = (UserDetailsAdapter) userDetails;
                Long userId = adapter.getUser().getId();

                if (!jwtService.isTokenVersionValid(jwt, userId)) {
                    log.info("Token version mismatch for user: {} — session invalidated by new login", userEmail);
                    authenticationEntryPoint.commence(request, response,
                            new BadCredentialsException("Your session has expired due to a new login from another device."));
                    return;
                }

                // Set authentication in SecurityContext
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT for user: {}", e.getClaims().getSubject());
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Token has expired. Please refresh your token.", e));
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Invalid token.", e));
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage(), e);
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Authentication failed.", e));
        }
    }
}
