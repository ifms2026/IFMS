# Dashboard API Contract

> Source of truth cho backend khi thiết kế API dashboard.
> Derived trực tiếp từ mock data và `api.get(...)` calls trong các component FE.
> File types tham chiếu: `types/dashboard.ts`, `types/request.ts`, `types/wallet.ts`, `types/accounting.ts`.

---

## Tổng quan chiến lược

Frontend dùng **2 chiến lược** tùy role:

| Chiến lược | Role | Mô tả |
|---|---|---|
| **Composition** | Employee, Team Leader | Gọi nhiều endpoint có sẵn song song, ghép data ở FE — không cần endpoint dashboard riêng |
| **Dedicated endpoint** | Manager, Accountant, CFO, Admin | Gọi `GET /api/v1/dashboard/{role}` trả snapshot tổng hợp + các sub-list endpoint |

---

## Pagination convention

> ⚠ Hiện tại FE đang dùng **2 convention khác nhau** — backend cần confirm và FE sẽ align:

| Endpoint group | Convention | Ví dụ |
|---|---|---|
| `/requests`, `/payslips`, `/cfo/approvals` | `page=1` (1-indexed) | `?page=1&limit=3` |
| `/manager/*`, `/accountant/*`, `/team-leader/*`, `/wallet/transactions` | `page=0` (0-indexed, Spring Pageable) | `?page=0&size=3` |

Wrapper response cho paginated list:
```json
{
  "success": true,
  "data": {
    "items": [...],
    "total": 42,
    "page": 1,
    "limit": 3
  }
}
```

---

## Role 1 — EMPLOYEE (Composition, không cần endpoint riêng)

Frontend gọi 5 endpoint song song bằng `Promise.allSettled`. Nếu bất kỳ endpoint nào lỗi, FE fallback sang mock data của field đó.

### Các endpoint cần thiết

#### `GET /api/v1/wallet`
Đã có. Response: `WalletResponse`.

```json
{
  "id": 1,
  "balance": 15500000,
  "lockedBalance": 2300000,
  "availableBalance": 13200000,
  "ownerType": "USER",
  "ownerId": 11
}
```

---

#### `GET /api/v1/requests/summary`
Trả số liệu tổng hợp yêu cầu của employee hiện tại (filter theo `userId` từ JWT).

Response: `RequestSummaryResponse`

```json
{
  "totalPendingApproval": 3,
  "totalApproved": 5,
  "totalRejected": 1,
  "totalPaid": 12,
  "totalCancelled": 0
}
```

---

#### `GET /api/v1/requests?status=PENDING&page=1&limit=3`
Danh sách yêu cầu PENDING của employee (1-indexed). Response: `PaginatedResponse<RequestListItem>`.

```json
{
  "items": [
    {
      "id": 101,
      "requestCode": "REQ-IT-0326-001",
      "type": "ADVANCE",
      "status": "PENDING",
      "amount": 1200000,
      "approvedAmount": null,
      "description": "Tạm ứng chi phí đi công tác",
      "rejectReason": null,
      "projectId": 5,
      "projectName": "Hệ thống quản lý nội bộ",
      "phaseId": 12,
      "phaseName": "Phase 2 - Development",
      "categoryId": 3,
      "categoryName": "Chi phí di chuyển",
      "createdAt": "2026-04-01T09:15:00",
      "updatedAt": "2026-04-01T09:15:00"
    }
  ],
  "total": 3,
  "page": 1,
  "limit": 3
}
```

---

#### `GET /api/v1/payslips?page=1&limit=1`
Payslip gần nhất của employee (1-indexed). Response: `PaginatedResponse<PayslipListItem>`.

```json
{
  "items": [
    {
      "id": 7,
      "payslipCode": "PS-2026-03",
      "periodName": "Tháng 3/2026",
      "finalNetSalary": 12500000,
      "status": "PAID"
    }
  ],
  "total": 7,
  "page": 1,
  "limit": 1
}
```

---

#### `GET /api/v1/wallet/transactions?page=0&size=5`
5 giao dịch gần nhất của ví (0-indexed, Spring Pageable). Response: `PaginatedResponse<TransactionResponse>` hoặc `{ content: TransactionResponse[] }`.

```json
{
  "items": [
    {
      "id": 1,
      "transactionCode": "TXN-8829145A",
      "type": "REQUEST_PAYMENT",
      "amount": 5000000,
      "status": "COMPLETED",
      "createdAt": "2026-03-28T14:30:00"
    },
    {
      "id": 2,
      "transactionCode": "TXN-7714209B",
      "type": "PAYSLIP_PAYMENT",
      "amount": 12500000,
      "status": "COMPLETED",
      "createdAt": "2026-03-25T09:00:00"
    }
  ],
  "total": 20,
  "page": 0,
  "size": 5
}
```

> `amount` âm nếu là giao dịch ra (WITHDRAW). `type` dùng enum `TransactionType`: `DEPOSIT | WITHDRAW | REQUEST_PAYMENT | PAYSLIP_PAYMENT | SYSTEM_ADJUSTMENT | DEPT_QUOTA_ALLOCATION | PROJECT_QUOTA_ALLOCATION`.

---

## Role 2 — TEAM LEADER (Composition, không cần endpoint riêng)

Frontend gọi 2 endpoint song song + lấy wallet từ `WalletContext` (đã fetch sẵn).

### Các endpoint cần thiết

#### `GET /api/v1/team-leader/approvals?page=0&size=3&status=PENDING`
3 yêu cầu PENDING cần TL duyệt (0-indexed). Response: `PaginatedResponse<TLApprovalListItem>`.

```json
{
  "items": [
    {
      "id": 1,
      "requestCode": "REQ-2026-0041",
      "type": "ADVANCE",
      "status": "PENDING",
      "amount": 3500000,
      "description": "Mua vật tư thiết bị thí nghiệm",
      "requester": {
        "id": 11,
        "fullName": "Đỗ Quốc Bảo",
        "avatar": null,
        "employeeCode": "EMP001",
        "jobTitle": "Frontend Developer",
        "email": "emp.it1@ifms.vn"
      },
      "project": {
        "id": 1,
        "projectCode": "PRJ-IT-001",
        "name": "Hệ thống quản lý nội bộ"
      },
      "phase": {
        "id": 1,
        "phaseCode": "PH-001",
        "name": "Phase 1 - Phân tích",
        "budgetLimit": 50000000,
        "currentSpent": 12000000
      },
      "category": { "id": 1, "name": "Thiết bị & Phần cứng" },
      "attachments": [],
      "createdAt": "2026-04-03T09:15:00"
    }
  ],
  "total": 5,
  "page": 0,
  "size": 3
}
```

---

#### `GET /api/v1/team-leader/projects?page=0&size=3`
3 dự án TL đang quản lý (0-indexed). Response: `PaginatedResponse<TLProjectListItem>`.

```json
{
  "items": [
    {
      "id": 1,
      "projectCode": "PRJ-IT-001",
      "name": "Hệ thống quản lý nội bộ",
      "status": "ACTIVE",
      "totalBudget": 150000000,
      "availableBudget": 12000000,
      "totalSpent": 138000000,
      "memberCount": 5,
      "currentPhaseId": 2,
      "currentPhaseName": "Phase 2 - Triển khai",
      "createdAt": "2026-01-10T08:00:00"
    }
  ],
  "total": 3,
  "page": 0,
  "size": 3
}
```

---

## Role 3 — MANAGER

Frontend gọi 3 endpoint song song. `GET /api/v1/dashboard/manager` là endpoint **mới cần implement**.

### `GET /api/v1/dashboard/manager` ← **CẦN IMPLEMENT**

Trả snapshot tổng hợp phòng ban của Manager (filter theo `departmentId` từ JWT).

Response: `ManagerDashboardResponse`

```json
{
  "departmentBudget": {
    "totalProjectQuota": 800000000,
    "totalAvailableBalance": 524500000,
    "totalSpent": 275500000
  },
  "projectStatusSummary": {
    "active": 3,
    "planning": 2,
    "paused": 1,
    "closed": 0
  },
  "pendingApprovalsCount": 2,
  "teamDebtSummary": {
    "totalDebt": 5200000,
    "employeesWithDebt": 3
  }
}
```

**Ghi chú fields:**
- `departmentBudget.totalProjectQuota` = tổng quota đã cấp cho tất cả project của phòng ban
- `departmentBudget.totalAvailableBalance` = tổng số dư còn lại trong tất cả project fund
- `departmentBudget.totalSpent` = tổng đã chi = `totalProjectQuota - totalAvailableBalance`
- `teamDebtSummary.totalDebt` = tổng tiền ứng (ADVANCE) chưa hoàn của toàn team
- `teamDebtSummary.employeesWithDebt` = số nhân viên đang có advance chưa hoàn

---

### `GET /api/v1/manager/approvals?page=0&size=3`
3 PROJECT_TOPUP đang chờ Manager duyệt (0-indexed). Response: `PaginatedResponse<ManagerApprovalListItem>`.

```json
{
  "items": [
    {
      "id": 10,
      "requestCode": "REQ-2026-0050",
      "type": "PROJECT_TOPUP",
      "status": "PENDING",
      "amount": 50000000,
      "description": "Xin cấp vốn bổ sung Phase 2",
      "requester": {
        "id": 4,
        "fullName": "Hoàng Minh Tuấn",
        "avatar": null,
        "employeeCode": "TL001",
        "jobTitle": "Team Leader",
        "email": "tl.it@ifms.vn"
      },
      "project": {
        "id": 1,
        "projectCode": "PRJ-IT-001",
        "name": "Hệ thống quản lý nội bộ",
        "availableBudget": 12000000
      },
      "createdAt": "2026-04-03T10:00:00"
    }
  ],
  "total": 2,
  "page": 0,
  "size": 3
}
```

---

### `GET /api/v1/manager/projects?page=0&size=4`
4 dự án của phòng ban Manager (0-indexed). Response: `PaginatedResponse<ManagerProjectListItem>`.

```json
{
  "items": [
    {
      "id": 1,
      "projectCode": "PRJ-IT-001",
      "name": "Hệ thống quản lý nội bộ",
      "status": "ACTIVE",
      "totalBudget": 150000000,
      "availableBudget": 12000000,
      "totalSpent": 138000000,
      "memberCount": 5,
      "currentPhaseId": 2,
      "currentPhaseName": "Phase 2 - Triển khai",
      "createdAt": "2026-01-10T08:00:00"
    }
  ],
  "total": 6,
  "page": 0,
  "size": 4
}
```

> FE tính `budgetBurnPercent = (totalSpent / totalBudget) * 100` để render thanh tiến độ. Màu: `<65%` xanh, `65-85%` vàng, `>85%` đỏ.

---

## Role 4 — ACCOUNTANT

Frontend gọi 3 endpoint song song. `GET /api/v1/dashboard/accountant` là endpoint **mới cần implement**.

### `GET /api/v1/dashboard/accountant` ← **CẦN IMPLEMENT**

Response: `AccountantDashboardResponse`

```json
{
  "systemFundBalance": 1248500000,
  "pendingDisbursementsCount": 4,
  "monthlyInflow": 320000000,
  "monthlyOutflow": 187500000,
  "payrollStatus": {
    "latestPeriod": "Tháng 03/2026",
    "status": "COMPLETED"
  }
}
```

**Ghi chú fields:**
- `systemFundBalance` = số dư hiện tại của `COMPANY_FUND` wallet
- `pendingDisbursementsCount` = số request có status `APPROVED_BY_TEAM_LEADER` (đang chờ accountant giải ngân)
- `monthlyInflow` = tổng tiền vào COMPANY_FUND wallet trong tháng hiện tại
- `monthlyOutflow` = tổng tiền ra COMPANY_FUND wallet trong tháng hiện tại
- `payrollStatus.latestPeriod` = tên kỳ lương gần nhất (string tự do, ví dụ `"Tháng 03/2026"`)
- `payrollStatus.status` = enum `PayrollStatus`: `DRAFT | PROCESSING | COMPLETED`. Trả `null` nếu chưa có kỳ lương nào.

**Fund health threshold (FE tự tính, không cần BE trả):**
- `HEALTHY` ≥ 500,000,000
- `LOW` 100,000,000 – 499,999,999
- `CRITICAL` < 100,000,000

---

### `GET /api/v1/accountant/disbursements?page=0&size=3&status=APPROVED_BY_TEAM_LEADER`
3 yêu cầu chờ giải ngân (0-indexed). Response: `PaginatedResponse<DisbursementListItem>`.

```json
{
  "items": [
    {
      "id": 1,
      "requestCode": "REQ-2026-0041",
      "type": "ADVANCE",
      "status": "APPROVED_BY_TEAM_LEADER",
      "amount": 3500000,
      "approvedAmount": 3500000,
      "description": "Mua vật tư thiết bị cho phase 1.",
      "requester": {
        "id": 11,
        "fullName": "Đỗ Quốc Bảo",
        "avatar": null,
        "employeeCode": "EMP001",
        "jobTitle": "Frontend Developer",
        "departmentName": "Phòng IT",
        "bankName": "Vietcombank",
        "bankAccountNum": "001100220011",
        "bankAccountOwner": "DO QUOC BAO"
      },
      "project": {
        "id": 1,
        "projectCode": "PRJ-IT-001",
        "name": "Hệ thống quản lý nội bộ"
      },
      "createdAt": "2026-04-03T09:15:00"
    }
  ],
  "total": 4,
  "page": 0,
  "size": 3
}
```

---

### `GET /api/v1/accountant/payroll?page=0&size=1`
Kỳ lương gần nhất (0-indexed). Response: `PaginatedResponse<PayrollPeriodListItem>`.

```json
{
  "items": [
    {
      "id": 1,
      "periodCode": "PAYROLL-2026-03",
      "periodName": "Tháng 03/2026",
      "status": "COMPLETED",
      "totalEmployees": 12,
      "totalNetPayroll": 162500000,
      "createdAt": "2026-03-31T08:00:00"
    }
  ],
  "total": 3,
  "page": 0,
  "size": 1
}
```

---

## Role 5 — CFO

Frontend thử `GET /api/v1/cfo/dashboard` trước. Nếu lỗi (404/500), fallback tự compose từ 2 endpoint có sẵn.

### `GET /api/v1/cfo/dashboard` ← **CẦN IMPLEMENT** (primary)

Response: `CfoDashboardResponse`

```json
{
  "companyFundBalance": 1248500000,
  "pendingApprovalsCount": 2,
  "monthlyApprovedAmount": 500000000,
  "monthlyRejectedCount": 1,
  "recentApprovals": [
    {
      "id": 20,
      "requestCode": "REQ-2026-0060",
      "departmentName": "Phòng Công nghệ thông tin",
      "amount": 200000000,
      "status": "PENDING",
      "createdAt": "2026-04-02T09:00:00"
    },
    {
      "id": 21,
      "requestCode": "REQ-2026-0055",
      "departmentName": "Phòng Kinh doanh",
      "amount": 100000000,
      "status": "PENDING",
      "createdAt": "2026-04-01T11:00:00"
    }
  ]
}
```

**Ghi chú fields:**
- `companyFundBalance` = số dư wallet `COMPANY_FUND` hiện tại
- `pendingApprovalsCount` = số DEPARTMENT_TOPUP đang chờ CFO duyệt
- `monthlyApprovedAmount` = tổng `amount` các DEPARTMENT_TOPUP đã `APPROVED_BY_CFO` trong tháng hiện tại
- `monthlyRejectedCount` = số DEPARTMENT_TOPUP bị `REJECTED` trong tháng hiện tại
- `recentApprovals` = 5 DEPARTMENT_TOPUP gần nhất (bất kể status)

### Fallback compose (nếu `/cfo/dashboard` chưa sẵn sàng)

FE tự gọi song song:
```
GET /api/v1/company-fund           → CompanyFundResponse
GET /api/v1/cfo/approvals?status=PENDING&page=1&limit=5  → PaginatedResponse<AdminApprovalListItem>
```

---

## Role 6 — ADMIN

Frontend thử `GET /api/v1/admin/dashboard` trước. Nếu lỗi, fallback tự compose từ 4 endpoint có sẵn.

### `GET /api/v1/admin/dashboard` ← **CẦN IMPLEMENT** (primary)

Response: `AdminDashboardResponse`

```json
{
  "totalUsers": 64,
  "totalDepartments": 8,
  "totalWalletBalance": 2450000000,
  "recentAuditEvents": [
    {
      "id": 1,
      "actorName": "Admin System",
      "action": "USER_CREATED",
      "entityName": "users",
      "createdAt": "2026-04-09T08:15:00"
    },
    {
      "id": 2,
      "actorName": "Admin System",
      "action": "DEPARTMENT_UPDATED",
      "entityName": "departments",
      "createdAt": "2026-04-09T07:40:00"
    }
  ]
}
```

**Ghi chú fields:**
- `totalUsers` = tổng số user đang `ACTIVE` trong hệ thống
- `totalDepartments` = tổng số phòng ban
- `totalWalletBalance` = tổng số dư tất cả ví **cá nhân** (ownerType = `USER`), không tính project/department fund
- `recentAuditEvents` = 4–5 audit log gần nhất, `actorName` null nếu là hệ thống tự động

### Fallback compose (nếu `/admin/dashboard` chưa sẵn sàng)

FE tự gọi song song:
```
GET /api/v1/admin/users?page=1&limit=1           → lấy total từ PaginatedResponse
GET /api/v1/admin/departments?page=1&limit=1     → lấy total từ PaginatedResponse
GET /api/v1/admin/audit?page=1&limit=5           → lấy recentAuditEvents
GET /api/v1/company-fund                         → lấy companyFundBalance làm fallback
```

---

## Chart data — KHÔNG cần API

Các chart sau đang dùng **hardcode mock** trong FE component, backend không cần implement cho MVP:

| Dashboard | Chart | Lý do |
|---|---|---|
| Accountant | Cash flow area chart (inflow/outflow theo tháng) | Mock trong `CASHFLOW` constant, comment "replace when analytics API available" |
| Admin | Cash flow area chart + Department spending donut | Mock trong `CASHFLOW` + `DEPT_SPENDING` constant |
| Admin | Top debtors table | Mock trong `TOP_DEBTORS` constant |
| Employee | Monthly spending bar chart | Mock trong `MOCK_MONTHLY` constant |

---

## Wrapper response format

Tất cả endpoint trả về wrapper `ApiResponse<T>` chuẩn:

```json
{
  "success": true,
  "message": "OK",
  "data": { ... },
  "timestamp": "2026-04-09T08:00:00Z"
}
```

Khi lỗi:
```json
{
  "success": false,
  "message": "Không tìm thấy tài nguyên",
  "data": null,
  "timestamp": "2026-04-09T08:00:00Z"
}
```

FE đọc `response.data` trực tiếp qua `api-client.ts` (đã unwrap sẵn).

---

## Priority implement

| Ưu tiên | Endpoint | Role |
|---|---|---|
| 🔴 P1 | `GET /api/v1/dashboard/manager` | Manager |
| 🔴 P1 | `GET /api/v1/dashboard/accountant` | Accountant |
| 🟡 P2 | `GET /api/v1/cfo/dashboard` | CFO |
| 🟡 P2 | `GET /api/v1/admin/dashboard` | Admin |
| ✅ Không cần | — | Employee, Team Leader (dùng endpoint có sẵn) |
