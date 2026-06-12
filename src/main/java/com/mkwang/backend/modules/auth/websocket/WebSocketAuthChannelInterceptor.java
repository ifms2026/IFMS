package com.mkwang.backend.modules.auth.websocket;

import com.mkwang.backend.common.exception.WebSocketAccountException;
import com.mkwang.backend.common.exception.WebSocketAuthException;
import com.mkwang.backend.modules.auth.security.JwtService;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Interceptor xác thực JWT tại WebSocket handshake (STOMP CONNECT frame).
 * <p>
 * Flow: client gửi STOMP CONNECT với header
 * {@code Authorization: Bearer <token>}
 * → interceptor extract JWT → validate chữ ký + hết hạn + token version
 * (single-session)
 * → set Authentication vào principal.
 * Các frame sau (SEND, SUBSCRIBE) kế thừa authentication đã được thiết lập.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        // Chỉ xác thực tại thời điểm CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("WebSocket CONNECT rejected: missing or invalid Authorization header");
                throw new WebSocketAuthException("Missing or invalid Authorization header");
            }

            String jwt = authHeader.substring(BEARER_PREFIX.length());

            try {
                String username = jwtService.extractUsername(jwt);

                if (username == null) {
                    throw new WebSocketAuthException("Cannot extract username from token");
                }

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Kiểm tra trạng thái tài khoản
                if (!userDetails.isEnabled()) {
                    throw new WebSocketAccountException("Account is disabled. Contact administrator.");
                }
                if (!userDetails.isAccountNonLocked()) {
                    throw new WebSocketAccountException("Account is locked. Contact administrator.");
                }

                // Validate chữ ký + hạn sử dụng token
                if (!jwtService.isTokenValid(jwt, userDetails)) {
                    throw new WebSocketAuthException("Token is invalid or expired");
                }

                // ── Single-session check: version trong token vs Redis/DB ──
                UserDetailsAdapter adapter = (UserDetailsAdapter) userDetails;
                Long userId = adapter.getUser().getId();

                if (!jwtService.isTokenVersionValid(jwt, userId)) {
                    log.info(
                            "WebSocket CONNECT rejected: token version mismatch for user={} — session invalidated by new login",
                            username);
                    throw new WebSocketAuthException("Tài khoản đã đăng nhập ở thiết bị khác.");
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                // Đặt authentication vào STOMP session — được kế thừa bởi mọi frame sau
                accessor.setUser(authentication);

                log.debug("WebSocket authenticated: user={}", username);

            } catch (WebSocketAuthException | WebSocketAccountException e) {
                throw e; // Re-throw domain exceptions as-is
            } catch (ExpiredJwtException e) {
                log.debug("WebSocket CONNECT rejected: expired JWT for user={}", e.getClaims().getSubject());
                throw new WebSocketAuthException("Token has expired. Please refresh your token.", e);
            } catch (JwtException e) {
                log.warn("WebSocket CONNECT rejected: invalid JWT — {}", e.getMessage());
                throw new WebSocketAuthException("Invalid JWT token", e);
            } catch (Exception e) {
                log.error("WebSocket authentication error: {}", e.getMessage(), e);
                throw new WebSocketAuthException("Authentication failed.", e);
            }
        }

        return message;
    }
}
