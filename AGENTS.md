# IFMS Backend — Global Conventions

## Project Overview

**Internal Financial Management System (IFMS)** — Spring Boot 3.4.1 backend for managing company cash flow: department budgets, project funding, employee expenses, payroll.

- **Base URL:** `http://localhost:8080/api/v1`
- **Package root:** `com.mkwang.backend`
- **DB:** PostgreSQL (`IFMSdb`), migrations via Flyway (V1–V14)
- **Author / git user:** MKwang

---

## Tech Stack (Summary)

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4.1 |
| Security | Spring Security + JWT (JJWT) + `@EnableMethodSecurity` |
| ORM | Spring Data JPA + Hibernate 6 |
| DB | PostgreSQL + Flyway migrations |
| Cache / Session | Redis (Lettuce pool) |
| Messaging | RabbitMQ (AMQP) — email queue + audit queue |
| Email | Brevo API (transactional email) |
| File Storage | Cloudinary (upload signature pattern) |
| Realtime | Server-Sent Events (SSE) — `SseService` + `SseEmitter` |
| Docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |
| Build | Maven (pom.xml), Lombok |

---

## Module Structure (Summary)

```
src/main/java/com/mkwang/backend/
├── config/                   # Global Spring configs
├── common/
│   ├── base/BaseEntity.java  # MappedSuperclass: created_at, updated_at, created_by, updated_by
│   ├── dto/ApiResponse.java  # Standard response wrapper
│   ├── exception/            # Custom exceptions + GlobalExceptionHandler
│   └── utils/                # BusinessCodeGenerator, PinValidator, etc.
└── modules/
    ├── auth/         # JWT auth, login/logout, password reset
    ├── audit/        # Audit logging (Hibernate → RabbitMQ → DB)
    ├── mail/         # Email (RabbitMQ + Brevo)
    ├── file/         # Cloudinary upload signatures
    ├── user/         # User, Role, Permission, Profile, SecuritySettings
    ├── profile/      # UserProfile, UserSecuritySettings
    ├── organization/ # Department
    ├── project/      # Project, Phase, Members, Budget, ExpenseCategory, PhaseCategoryBudget
    ├── request/      # Request, History, Attachment, AdvanceBalance
    ├── wallet/       # Wallet, Transaction, LedgerEntry (double-entry)
    ├── accounting/   # Payroll, Payslip, SystemFund
    ├── notification/ # SSE realtime + RabbitMQ consumer + persistence
    └── config/       # SystemConfig
```

For detailed domain architecture, see:
- **[docs/financial-architecture.md](../docs/financial-architecture.md)** — 4-tier wallet, flows, AdvanceBalance
- **[docs/rbac-model.md](../docs/rbac-model.md)** — Roles, permissions, seeded users

---

## Global Conventions

### Response Format

**Tất cả endpoints** phải trả về `ResponseEntity<ApiResponse<T>>`:

```java
public ResponseEntity<ApiResponse<UserDto>> getUser(Long id) {
    UserDto user = userService.getUser(id);
    return ResponseEntity.ok(ApiResponse.success(user));
}
```

Không được dùng: void, raw object, List, Page, v.v. — **phải wrap bằng ApiResponse**.

**Pagination convention (bắt buộc):**
- **Mọi endpoint có phân trang** phải dùng `com.mkwang.backend.common.dto.PageResponse<T>` làm `data` bên trong `ApiResponse`. Không được tạo DTO phân trang riêng theo module (ví dụ `*PageResponse` custom, `*ListResponse` custom).
- Fields chuẩn: `items`, `total`, `page`, `size`, `totalPages`.
- Controller và Service **phải khai báo return type là `PageResponse<XxxResponse>`**, không dùng `Page<T>`, `List<T>`, hay bất kỳ wrapper khác.

```java
// ✓ CORRECT
public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
    PageResponse<UserResponse> result = userService.getUsers(page, size);
    return ResponseEntity.ok(ApiResponse.success(result));
}

// ✗ WRONG — không được trả thẳng List hoặc Page
public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers(...) { ... }
```

---

### Dynamic Filtering — Specification (bắt buộc cho pagination có filter)

**Mọi pagination endpoint có từ 2 filter trở lên** phải dùng `JpaSpecificationExecutor` + một class `XxxSpecification` riêng.

**Không được dùng:** `@Query` với `(:param IS NULL OR ...)` trick, inline predicate list trong service, hoặc nhiều repository method cho từng tổ hợp filter.

**Cấu trúc bắt buộc:**

**1. Repository** — extends thêm `JpaSpecificationExecutor<Entity>`:
```java
@Repository
public interface RequestRepository
        extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> { }
```

**2. Specification class** — đặt trong `modules/{module}/repository/`, tên `XxxSpecification`:
- Mỗi predicate là một **pure static method** — không chứa null-check bên trong
- Method `filter(...)` là **combiner duy nhất** — chịu trách nhiệm null-check và compose các predicate bằng `Specification.where().and()`

```java
// modules/request/repository/RequestSpecification.java
public class RequestSpecification {

   private RequestSpecification() {}

   public static Specification<Request> hasRequester(Long userId) {
      return (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
   }

   public static Specification<Request> hasType(RequestType type) {
      return (root, query, cb) ->
              type == null ? null : cb.equal(root.get("type"), type);
   }

   public static Specification<Request> hasStatus(RequestStatus status) {
      return (root, query, cb) ->
              status == null ? null : cb.equal(root.get("status"), status);
   }

   public static Specification<Request> matchesSearch(String search) {
      return (root, query, cb) -> {
         if (search == null || search.isBlank()) return null;
         String pattern = "%" + search.toLowerCase() + "%";
         return cb.or(
                 cb.like(cb.lower(root.get("requestCode")), pattern),
                 cb.like(cb.lower(root.get("description")), pattern)
         );
      };
   }

   public static Specification<Request> filter (Long userId, RequestType type, RequestStatus status, String search) {
      return Specification.where(hasRequester(userId))
              .and(hasType(type))
              .and(hasStatus(status))
              .and(matchesSearch(search));
   }
}
```

**3. Service** — gọi `filter(...)` để lấy `Specification`, assign vào biến, rồi truyền vào `findAll`:
```java
// ✓ CORRECT
Specification<Request> spec = RequestSpecification.filter(userId, type, status, search);
Page<RequestSummaryResponse> result = requestRepository.findAll(spec, pageable)
        .map(requestMapper::toSummaryResponse);

// ✗ WRONG — không chain predicates trực tiếp trong service
Specification<Request> spec = Specification
        .where(RequestSpecification.hasType(type))
        .and(RequestSpecification.hasStatus(status));
```

---

### Exception Handling

**KHÔNG ĐƯỢC throw:** `RuntimeException`, `IllegalArgumentException`, `IllegalStateException`, `UnsupportedOperationException`, hay bất kỳ raw exception nào.

**Bắt buộc:** Tất cả exceptions phải `extends BaseException` (`com.mkwang.backend.common.exception.BaseException`).

**Available exceptions:**
- `BadRequestException` (400) — validation, business rule violation, invalid input
- `ResourceNotFoundException` (404) — entity not found
- `UnauthorizedException` (401) — insufficient permissions
- `InternalSystemException` (500) — infrastructure error, config missing
- Domain-specific: `InsufficientWalletBalanceException`, `AdvanceBalanceAlreadySettledException`, etc.

**Creating new exceptions:**
- Extend `BaseException`
- Pass `HttpStatus` + `errorCode` explicitly
- Place in `common/exception/`
- Example:
  ```java
  public class CustomException extends BaseException {
      public CustomException(String message) {
          super(message, HttpStatus.BAD_REQUEST, "CUSTOM_CODE");
      }
  }
  ```

**GlobalExceptionHandler** (`common/exception/GlobalExceptionHandler.java`):
- Every exception that can occur in an endpoint **must have** its own `@ExceptionHandler`
- Log with appropriate level (ERROR for 500, WARN for 4xx)
- Return `ResponseEntity<ApiResponse<T>>` with appropriate HTTP status
- Handlers are ordered: specific → general
- Current handlers cover: BaseException (all subclasses), Auth (Authentication, BadCredentials, AccessDenied), Validation (MethodArgumentNotValid, ConstraintViolation), HTTP (HttpMessageNotReadable, MissingRequestParameter, MethodNotSupported, MediaTypeNotSupported, NoHandlerFound), DB (DataIntegrityViolation, OptimisticLocking), generic Exception (500)

---

### DTO Conventions

All DTOs must be split into exactly 2 groups by purpose:
- `modules/{module}/dto/request` → input DTOs
- `modules/{module}/dto/response` → output DTOs

Naming is mandatory:
- DTOs in `dto/request` **must end with** `Request`
- DTOs in `dto/response` **must end with** `Response`

Usage rules:
- `dto/request`: used as input parameters for endpoint methods (Controller) and service methods (`Service` / `ServiceImpl`)
- `dto/response`: used as return types for endpoint methods and service methods
- Do not create generic `*Dto` names for new code; use `*Request` or `*Response` based on direction

All DTO fields **must use camelCase** (e.g. `firstName`, `departmentId`).

Example (module `auth`):
- `modules/auth/dto/request/LoginRequest.java` is used as controller/service input
- `modules/auth/dto/response/AuthenticationResponse.java` is used as service/controller output

```java
// AuthController.java
@PostMapping("/login")
public ResponseEntity<ApiResponse<AuthenticationResponse>> login(
        @Valid @RequestBody LoginRequest request) {
    AuthenticationResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response));
}

// AuthService.java
AuthenticationResponse login(LoginRequest request);
```

---

### Mapper Pattern

**DO NOT write private mapper methods** (e.g. `toDto()`, `toEntity()`) directly in Service.

**Instead:** Each domain must have **one `@Component` mapper** in `modules/{module}/mapper/`:

```java
// modules/user/mapper/UserMapper.java
@Component
public class UserMapper {
    public UserDto toDto(User user) { ... }
    public User toEntity(CreateUserRequest req) { ... }
}

// UserServiceImpl.java
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    
    public UserDto getUser(Long id) {
        User user = userRepository.findById(id);
        return userMapper.toDto(user);  // ← use mapper
    }
}
```

Responsibilities:
- `entity → responseDto` (API responses)
- `requestDto → entity` (create/update)

---

### Cross-Domain Service Dependencies

When a service needs functionality from **another domain**, it must inject that domain's **Service interface** — never its repository directly.

```java
// ✓ CORRECT — inject ProfileService, not UserProfileRepository
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final ProfileService profileService;

    public void onboardUser(OnboardUserRequest request) {
        // ...
        profileService.createProfile(user, employeeCode, jobTitle, phone);
    }
}

// ✗ WRONG — reaching into another domain's repository
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserProfileRepository userProfileRepository; // ❌ belongs to profile domain
}
```

**If the needed method does not exist yet** in the target domain's service:
1. **First** — declare the method in the target domain's `Service` interface
2. **Then** — implement it in the corresponding `ServiceImpl`
3. **Finally** — call it from the current service

This applies to all cross-domain calls: `user → profile`, `request → wallet`, `payroll → wallet`, etc.

**Exception:** A service may directly inject repositories **from its own domain only**.

---

### Configuration Conventions

**Every configurable value** (TTL, timeout, retry limit, code length, etc.) that can vary by environment **must be in `application.yml`** and injected via `@Value`:

```yaml
# application.yml
app:
  auth:
    setup-token-ttl-minutes: 15
  request:
    max-attachment-size-mb: 10
```

```java
@Service
public class AuthServiceImpl {
    @Value("${app.auth.setup-token-ttl-minutes}")
    private long setupTokenTtlMinutes;
}
```

**DO NOT hardcode:** `private static final long TTL = 15;` ❌

**Naming scheme:** `app.<module>.<property>` for organization.

---

### Method-Level Security (Service Layer)

Use `@PreAuthorize("hasAuthority('PERMISSION_NAME')")` on **ServiceImpl methods** (NOT Controller):

```java
@Service
public class RequestServiceImpl implements RequestService {
    @PreAuthorize("hasAuthority('REQUEST_APPROVE')")
    public void approveRequest(Long requestId) {
        // ...
    }
}
```

Controllers only call service methods — authorization is handled at service level.

For permission naming convention, see **[docs/rbac-model.md](../docs/rbac-model.md)**.

---

### Realtime Updates — SSE (Server-Sent Events)

**Dự án dùng SSE thay cho WebSocket** cho mọi realtime update từ server → client.

**Không được dùng:** Spring WebSocket, STOMP, SockJS cho bất kỳ realtime feature nào.

**Infrastructure:**
- `common/sse/SseService.java` — quản lý tất cả `SseEmitter` connections theo `userId`
- `common/dto/SseEvent.java` — wrapper cho SSE payload (`event`, `data`)
- Client subscribe tại `GET /notifications/stream` (produces `text/event-stream`)

**Flow chuẩn (notification làm ví dụ):**
1. Publisher publish `NotificationEvent` lên RabbitMQ queue
2. `NotificationConsumer` nhận event → **persist vào DB trước**
3. Sau khi persist thành công → `sseService.sendToUser(userId, SseEvent)` (best-effort)
4. Nếu SSE push fail (user offline) → bỏ qua, log WARN — notification vẫn còn trong DB

**Convention khi thêm realtime feature mới:**
- Inject `SseService` vào consumer/service cần push realtime
- Luôn wrap SSE push trong try-catch, log WARN nếu fail — **không throw exception**
- Persist state vào DB trước khi push SSE — đảm bảo idempotency khi user reconnect

```java
// ✓ CORRECT — persist first, SSE push best-effort
Notification saved = notificationRepository.save(notification);
try {
    sseService.sendToUser(userId, SseEvent.builder()
            .event("notification")
            .data(mapper.toDto(saved))
            .build());
} catch (Exception e) {
    log.warn("[Consumer] SSE push failed for userId={}: {}", userId, e.getMessage());
}
```

---

### Database Migrations

**Any change affecting schema** (add/drop/rename column, add table, add index, add constraint, change data type) **must come with a new Flyway migration** in `src/main/resources/db/migration/`.

**Naming:** `V{N}__{DESCRIPTION}.sql` where N is next version (currently V11).

**Rules:**
- Migrations are idempotent-safe: use `IF NOT EXISTS`, `IF EXISTS`
- **Never modify existing migration files** — create new ones only
- Flyway manages schema completely (`ddl-auto: validate`)

Example:
```sql
-- V12__Add_email_verified_to_users.sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);
```

---

## Domain-Specific Documentation

For domain-specific architecture and conventions, refer to:

1. **[docs/financial-architecture.md](../docs/financial-architecture.md)**
   - 4-tier wallet model, double-entry ledger
   - Request approval flows (Flow 1/2/3, SoD principle)
   - AdvanceBalance lifecycle

2. **[docs/entity-conventions.md](../docs/entity-conventions.md)**
   - BaseEntity MappedSuperclass
   - Append-only entities (AuditLog, LedgerEntry)
   - Pessimistic locking, @Version
   - Money field (BigDecimal precision)
   - Enum mapping, JSONB fields

3. **[docs/security-architecture.md](../docs/security-architecture.md)**
   - JWT tokens (access 30m, refresh 7d)
   - Single-session enforcement (token version)
   - SSE authentication (Bearer token qua query param hoặc header)
   - Audit system (async, Hibernate → RabbitMQ)
   - Mail system (async, Brevo)

4. **[docs/business-codes.md](../docs/business-codes.md)**
   - Code generation patterns (EMPLOYEE, PROJECT, PHASE, REQUEST, etc.)
   - BusinessCodeType enum, SequenceService, CodeFormatUtils
   - PostgreSQL sequences

5. **[docs/rbac-model.md](../docs/rbac-model.md)**
   - 6 roles (EMPLOYEE, TEAM_LEADER, MANAGER, ACCOUNTANT, CFO, ADMIN)
   - Permission naming convention (RESOURCE_ACTION)
   - Seeded users (test accounts)

6. **[docs/deposit-withdraw.md](../docs/deposit-withdraw.md)**
   - Deposit/withdraw implementation plan (VNPay + MockBank)
   - End-to-end flows, idempotency, and demo checklist

7. **[docs/system-config.md](../docs/system-config.md)**
   - System config key-value model and Redis cache strategy
   - TTL, cache annotations, and admin management APIs

8. **[docs/API_Spec.md](../docs/API_Spec.md)**
   - API contract by role/module
   - Request/response examples and integration notes

9. **[docs/file-storage.md](../docs/file-storage.md)**
   - Cloudinary upload-signature flow and upload folders
   - `FileStorageService` usage and metadata lifecycle

10. **[docs/mail.md](../docs/mail.md)**
   - Async mail architecture (Strategy pattern)
   - RabbitMQ routing, retry, and DLQ conventions

11. **[docs/notification.md](../docs/notification.md)**
   - Notification event flow (publish → RabbitMQ → consumer)
   - Persistence (DB) + SSE realtime push (`SseService`) + read/unread APIs
   - SSE endpoint: `GET /notifications/stream` (produces `text/event-stream`)
   - Consumer pattern: persist TRƯỚC → SSE push SAU (best-effort, user có thể offline)

---

## Getting Help

- **Project structure:** See module structure above
- **Domain architecture:** Check `/docs` folder for detailed guides
- **Code examples:** Search by entity name (User, Wallet, Request, etc.)
- **Conventions:** This file (global) + `/docs` (domain-specific)
