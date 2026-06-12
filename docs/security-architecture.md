# Security Architecture — IFMS

## JWT Token Strategy

**Stateless authentication** — không lưu token vào DB.

- **Access token:** 30 phút (lưu ở client)
- **Refresh token:** 7 ngày (dùng để lấy token mới)

### JWT Claims

```json
{
  "sub": "user@example.com",       // email (unique identifier)
  "ver": 3,                         // tokenVersion (từ DB)
  "authorities": ["PERMISSION_1", "PERMISSION_2"],  // permissions
  "iat": 1234567890,
  "exp": 1234567890
}
```

---

## Single-Session Enforcement

Prevent nhiều session cùng lúc bằng token version:

```
User login → token issued với ver=3
Redis cache: "token_ver:userId" = 3

[User tạo token mới / logout / đổi password]
  → ver tăng lên 4
  → Redis: "token_ver:userId" = 4

[Client gửi request với token cũ ver=3]
  → JwtAuthenticationFilter so sánh
  → token.ver (3) != cache.ver (4) → UNAUTHORIZED
```

---

## Method-Level Security

Phân quyền dùng `@EnableMethodSecurity` + `@PreAuthorize` trên **ServiceImpl method** (không Controller):

```java
@Service
public class WalletServiceImpl implements WalletService {
    @PreAuthorize("hasAuthority('WALLET_TRANSFER')")
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        // ...
    }
}
```

Permissions stored in `role_permissions` table (dynamic RBAC).

---

## Public Endpoints (Whitelist)

Các endpoint này không cần JWT:

```
POST /auth/login
POST /auth/refresh-token
POST /auth/forgot-password
POST /auth/verify-password-reset
POST /auth/reset-password
/ws/**              (WebSocket — JWT auth ở STOMP channel level)
/v3/api-docs/**     (Swagger docs)
/swagger-ui/**
/actuator/**
```

---

## WebSocket Authentication

STOMP handshake → extract JWT từ header → verify signature + token version.

Nếu JWT fail / expired / user disabled → `WebSocketAuthException` / `WebSocketAccountException`.

---

## Audit System (Async, không block request)

```
[Request xử lý xong, DB commit]
    ↓
[Hibernate PostCommit event]
    ↓
[FinancialAuditListener.onPostCommit()]
    ↓
[AuditPublisher.publishAsync() — @Async thread]
    ↓
[RabbitMQ auditExchange]
    ↓
[AuditLogConsumer.consume()]
    ↓
[INSERT audit_logs]
```

**AuditContextHolder** (ThreadLocal):
- Set `actorId` từ JWT (authenticated endpoints)
- Set `actorId` thủ công cho public endpoints (login, logout)
- Set `traceId` (request ID) per-request
- Blacklist: `AuditLog` entity (tránh infinite loop)

---

## Mail System (Async, RabbitMQ + Brevo)

```
[Service gọi MailPublisher.publish(MailType, to, subject, content)]
    ↓
[MailPublisher.publish() → MailStrategy.publish()]
    ↓
[RabbitMQ mailExchange]
    ↓
[MailConsumer @RabbitListener (concurrency 2-5)]
    ↓
[BrevoMailService.send*()]
    ↓
[Brevo API email]
```

**Retry:** Spring AMQP auto-retry 3 lần, exponential backoff 3s→9s.
Hết retries → NACK → route sang DLQ (Dead Letter Queue).

**Mail types:**
- `ONBOARD` — new user account creation
- `FORGET_PASSWORD` — OTP khi quên mật khẩu
- `WARNING` — alert/warning email

---

## Infrastructure Env Vars

```
# Database
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD

# Redis
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD

# RabbitMQ
RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USERNAME, RABBITMQ_PASSWORD

# External APIs
CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET
BREVO_API_KEY, MAIL_FROM_EMAIL, MAIL_FROM_NAME

# Security
JWT_SECRET_KEY
JWT_EXPIRATION (minutes, default 30)
JWT_REFRESH_EXPIRATION (days, default 7)

# OTP
OTP_TTL_MS (default 300000 = 5 phút)
OTP_LENGTH (default 6)
```

---

## JPA/Hibernate Config

- **ddl-auto:** `validate` — schema managed hoàn toàn bởi Flyway (không auto-create)
- **open-in-view:** `false` — không lazy load trong controller
- **dialect:** PostgreSQL (9.5+)
