package com.mkwang.backend.modules.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper; // Inject Spring-managed singleton

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        log.warn("Authentication error: {} - {}", request.getRequestURI(), authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        int status = HttpServletResponse.SC_UNAUTHORIZED;

        if (authException instanceof DisabledException || authException instanceof LockedException) {
            status = HttpServletResponse.SC_FORBIDDEN;
        }

        response.setStatus(status);

        ApiResponse<Object> apiResponse = ApiResponse.error(authException.getMessage());
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
