package com.mkwang.backend.modules.auth.security;

import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JWT Service — Stateless JWT với Single-Session enforcement qua tokenVersion.
 * <p>
 * Kiến trúc:
 * <ul>
 *   <li>Access Token + Refresh Token đều KHÔNG lưu vào DB — pure stateless.</li>
 *   <li>Single-session + logout: tăng {@code tokenVersion} trong User entity và Redis cache.</li>
 *   <li>Mỗi token chứa claim {@code "ver"} → Filter so sánh với Redis/DB để validate.</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    private final SecretKey signingKey;
    private final JwtParser jwtParser;
    private final long jwtExpiration;
    private final long refreshExpiration;

    private static final String TOKEN_VERSION_PREFIX = "token_ver:";
    private static final String VERSION_CLAIM = "ver";

    public JwtService(
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            @Value("${application.security.jwt.secret-key}") String secretKey,
            @Value("${application.security.jwt.expiration}") long jwtExpiration,
            @Value("${application.security.jwt.refresh-token.expiration}") long refreshExpiration) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);

        this.jwtParser = Jwts.parser()
                .verifyWith(this.signingKey)
                .build();
    }

    // ── Extract Claims ─────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract token version ("ver" claim) từ JWT.
     * Trả 0 nếu claim không tồn tại (token cũ trước khi có single-session).
     */
    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> {
            Object ver = claims.get(VERSION_CLAIM);
            return ver instanceof Number ? ((Number) ver).intValue() : 0;
        });
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired for subject: {}", e.getClaims().getSubject());
            throw e;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature/format: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
            throw e;
        }
    }

    // ── Generate Tokens (Stateless — không lưu DB) ─────────────

    public String generateToken(UserDetails userDetails, int tokenVersion) {
        return buildToken(userDetails, tokenVersion, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails, int tokenVersion) {
        return buildToken(userDetails, tokenVersion, refreshExpiration);
    }

    private String buildToken(UserDetails userDetails, int tokenVersion, long expiration) {
        long now = System.currentTimeMillis();

        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toList());
        List<String> permissions = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toList());

        Map<String, Object> claims = new HashMap<>();
        claims.put(VERSION_CLAIM, tokenVersion);
        claims.put("roles", roles);
        claims.put("permissions", permissions);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration))
                .signWith(signingKey)
                .compact();
    }

    // ── Validate Tokens ────────────────────────────────────────

    /**
     * Validate Access Token: kiểm tra chữ ký + hạn sử dụng (stateless).
     * Version được kiểm tra riêng ở {@link #isTokenVersionValid}.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Validate Refresh Token: kiểm tra chữ ký + hạn sử dụng.
     * Stateless — không check DB revoked nữa, version check thay thế.
     */
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        return isTokenValid(token, userDetails);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // ── Token Version — Redis + DB ─────────────────────────────

    /**
     * Lưu tokenVersion vào Redis với TTL = refreshExpiration.
     * Key: "token_ver:{userId}"
     */
    public void cacheTokenVersion(Long userId, int version) {
        String key = TOKEN_VERSION_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(version),
                refreshExpiration, TimeUnit.MILLISECONDS);
        log.debug("Cached token version: userId={}, version={}", userId, version);
    }

    /**
     * Xóa Redis cache khi không cần giữ version nữa (vd: sau logout nếu muốn clean).
     */
    public void evictTokenVersionCache(Long userId) {
        redisTemplate.delete(TOKEN_VERSION_PREFIX + userId);
    }

    /**
     * Lấy tokenVersion: ưu tiên Redis → fallback DB.
     */
    public int getTokenVersion(Long userId) {
        String key = TOKEN_VERSION_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        // Fallback: query DB + re-cache
        int dbVersion = userRepository.findById(userId)
                .map(User::getTokenVersion)
                .orElse(0);
        cacheTokenVersion(userId, dbVersion);
        return dbVersion;
    }

    /**
     * Kiểm tra version trong token có khớp version hệ thống không.
     * version_trong_token < version_hệ_thống → đã login/logout ở thiết bị khác.
     */
    public boolean isTokenVersionValid(String token, Long userId) {
        int tokenVersion = extractTokenVersion(token);
        int currentVersion = getTokenVersion(userId);
        return tokenVersion >= currentVersion;
    }

    // ── Getters ────────────────────────────────────────────────

    public long getJwtExpiration() { return jwtExpiration; }
    public long getRefreshExpiration() { return refreshExpiration; }
}
