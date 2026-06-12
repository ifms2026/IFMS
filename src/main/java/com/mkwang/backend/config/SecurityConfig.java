package com.mkwang.backend.config;

import com.mkwang.backend.modules.auth.security.JwtAuthenticationEntryPoint;
import com.mkwang.backend.modules.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final AuthenticationProvider authenticationProvider;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

        // Public endpoints — change-password, me, logout yêu cầu authenticated
        private static final String[] WHITE_LIST_URLS = {
                        "/auth/login",
                        "/auth/refresh-token",
                        "/auth/forgot-password",
                        "/auth/verify-password-reset",
                        "/auth/reset-password",
                        "/auth/first-login/complete",
                        "/test/public",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/banks",
                        "/actuator/**",
                        // WebSocket handshake — JWT auth handled at STOMP channel level
                        "/ws/**",
                        // Payment gateway callback/return URL must be public
                        "/payments/*/ipn",
                        "/payments/*/return"
        };

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(WHITE_LIST_URLS).permitAll()
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * Ngăn Spring Boot auto-register JwtAuthenticationFilter như servlet filter riêng.
         * JwtAuthenticationFilter là @Component → Spring Boot sẽ tự đăng ký vào servlet chain (order 0),
         * ngoài việc nó đã được addFilterBefore() vào security chain.
         * Kết quả: chạy 2 lần/request (double-execution).
         * setEnabled(false) → chỉ chạy trong security chain, không phải servlet chain.
         */
        @Bean
        public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
                        JwtAuthenticationFilter filter) {
                FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
                bean.setEnabled(false);
                return bean;
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(List.of("*"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Last-Event-ID"));
                configuration.setExposedHeaders(List.of("Authorization"));
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public SecureRandom secureRandom() {
                return new SecureRandom();
        }
}
