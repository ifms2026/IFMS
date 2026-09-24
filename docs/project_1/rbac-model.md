# RBAC Model — IFMS

## 6 Core Roles

| Role | Trách nhiệm | Permissions (examples) |
|------|------------|----------------------|
| `EMPLOYEE` | Tạo request, xem ví cá nhân, xem project active | REQUEST_CREATE, WALLET_VIEW_OWN, PROJECT_VIEW |
| `TEAM_LEADER` | Duyệt Flow 1 (ADVANCE/EXPENSE/REIMBURSE), quản lý phase/budget/members | REQUEST_APPROVE, PHASE_MANAGE, MEMBER_MANAGE |
| `MANAGER` | Tạo project, duyệt Flow 2 (PROJECT_TOPUP), xem dashboard phòng | PROJECT_CREATE, REQUEST_APPROVE, TOPUP_APPROVE, DASHBOARD_DEPT |
| `ACCOUNTANT` | Giải ngân (payout), quản lý payroll, xem/nạp quỹ hệ thống | REQUEST_EXECUTE, PAYROLL_MANAGE, SYSTEM_FUND_MANAGE |
| `CFO` | Duyệt Flow 3 (DEPARTMENT_TOPUP), cấp quota phòng ban, global dashboard | TOPUP_APPROVE, QUOTA_MANAGE, DASHBOARD_GLOBAL |
| `ADMIN` | IAM (user CRUD, role manage), org structure, system config | **KHÔNG có financial perms** — user management only |

---

## Seeded Users (Test / Development)

Default password: `Ifms@2026`

| Email | Code | Role | Department | Purpose |
|-------|------|------|-----------|---------|
| `admin@ifms.vn` | MK000 | ADMIN | — | System admin, user management |
| `cfo@ifms.vn` | MK010 | CFO | Finance | Approve DEPARTMENT_TOPUP, global dashboard |
| `accountant@ifms.vn` | MK001 | ACCOUNTANT | Finance | Execute payments, payroll, system fund |
| `manager.it@ifms.vn` | MK002 | MANAGER | IT | Create projects, approve PROJECT_TOPUP (IT) |
| `manager.sales@ifms.vn` | MK003 | MANAGER | Sales | Create projects, approve PROJECT_TOPUP (Sales) |
| `tl.it@ifms.vn` | MK008 | TEAM_LEADER | IT | Approve ADVANCE/EXPENSE/REIMBURSE |
| `emp.it1@ifms.vn` | MK004 | EMPLOYEE | IT | Create requests, view own wallet |
| `emp.it2@ifms.vn` | MK005 | EMPLOYEE | IT | Create requests, view own wallet |
| `emp.sales1@ifms.vn` | MK006 | EMPLOYEE | Sales | Create requests, view own wallet |
| `emp.fin1@ifms.vn` | MK007 | EMPLOYEE | Finance | Create requests, view own wallet |
| `ifms.support.noreply@gmail.com` | MK999 | ADMIN | — | System notification support |

---

## Permission Model

**Dynamic RBAC:** Permissions stored in `role_permissions` table (DB-driven, not hardcoded enum).

Sử dụng `@EnableMethodSecurity` để enable:

```java
@Configuration
@EnableMethodSecurity(
    prePostEnabled = true,   // @PreAuthorize, @PostAuthorize
    securedEnabled = false,  // @Secured không sử dụng
    jsr250Enabled = false    // @RolesAllowed không sử dụng
)
public class SecurityConfig { }
```

---

## Permission Examples (Naming Convention)

Format: `RESOURCE_ACTION` (UPPER_SNAKE_CASE)

- `REQUEST_CREATE` — employee tạo request
- `REQUEST_VIEW` — xem request
- `REQUEST_APPROVE` — approve request (TL, Manager, CFO)
- `REQUEST_EXECUTE` — execute + payout (Accountant)
- `WALLET_VIEW_OWN` — xem ví cá nhân
- `WALLET_VIEW_ALL` — xem tất cả ví (CFO, Accountant)
- `PROJECT_CREATE` — tạo project
- `PROJECT_VIEW` — xem project
- `PHASE_MANAGE` — tạo/update phase, allocate budget
- `MEMBER_MANAGE` — add/remove member
- `TOPUP_APPROVE` — approve PROJECT_TOPUP hoặc DEPARTMENT_TOPUP
- `QUOTA_MANAGE` — set quota (CFO)
- `PAYROLL_MANAGE` — quản lý payroll period, upload payslips
- `SYSTEM_FUND_MANAGE` — manage system fund (topup, withdraw)
- `DASHBOARD_DEPT` — xem department dashboard
- `DASHBOARD_GLOBAL` — xem global dashboard (CFO)
- `USER_MANAGE` — CRUD user, assign roles (ADMIN only)
- `ROLE_MANAGE` — CRUD role (ADMIN only)

---

## Phân Quyền ở Service Layer

`@PreAuthorize` được đặt trên **ServiceImpl method** (không Controller):

```java
@Service
@Transactional
@Slf4j
public class RequestServiceImpl implements RequestService {

    @PreAuthorize("hasAuthority('REQUEST_APPROVE')")
    public void approveRequest(Long requestId) {
        // ...
    }

    @PreAuthorize("hasAuthority('REQUEST_EXECUTE')")
    public void executePayment(Long requestId) {
        // ...
    }
}
```

Controller chỉ gọi method — phân quyền handle ở service.

---

## Authorization Check in Business Logic

Nếu cần check permissions **runtime** (không @PreAuthorize):

```java
// Từ SecurityContextHolder
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
boolean hasPermission = authorities.stream()
    .anyMatch(a -> a.getAuthority().equals("RESOURCE_ACTION"));
```

Hoặc inject `AuthorizationChecker` bean nếu tạo helper method.
