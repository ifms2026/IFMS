# IFMS – API Specification

> **Base URL:** `https://api.ifms.vn/api/v1` (production) / `http://localhost:8080/api/v1` (local)  
> **Auth:** `Authorization: Bearer <accessToken>` (JWT access token)  
> **Phân trang:** `?page=0&size=20` (Spring pageable; một số endpoint cũ có thể dùng `limit`)  
> **Cập nhật:** 06/04/2026 — đồng bộ theo `.claude/CLAUDE.md` và thư mục `docs/`

### ⚠️ Bối cảnh kiến trúc hiện tại (nguồn chuẩn)

> Tài liệu này mô tả API cho **Internal Financial Management System (IFMS)** theo kiến trúc backend hiện tại:
> - **RBAC 6 vai trò:** `EMPLOYEE`, `TEAM_LEADER`, `MANAGER`, `ACCOUNTANT`, `CFO`, `ADMIN` (dynamic permissions trong DB)
> - **SoD (Segregation of Duties):** tách rõ **Decision** và **Execution** cho các nghiệp vụ tài chính
> - **Wallet-first architecture:** mô hình ví nhiều tầng + ledger bất biến (append-only)
> - **Boundary control:** `FLOAT_MAIN` dùng để kiểm tra toàn vẹn hệ thống (invariant)
> - **Không dùng cơ chế escalation nhiều tầng** cho luồng request; approver/executor được xác định theo loại nghiệp vụ và permission

> **Lưu ý trạng thái triển khai:** File này là API contract tổng hợp cho frontend/integration; một số API có thể đang ở mức kế hoạch hoặc rollout dần. Khi có khác biệt, ưu tiên đối chiếu code + migration hiện tại và `docs/implementation-status.md`.

### Response Wrapper — `ApiResponse<T>`

Mọi endpoint chuẩn đều trả `ResponseEntity<ApiResponse<T>>`. Cấu trúc chung:

```json
{
  "success": true,
  "message": "Success",
  "data": { … },
  "timestamp": "2026-02-24T10:30:00"
}
```

| Field       | Type            | Mô tả                                                                 |
|-------------|-----------------|------------------------------------------------------------------------|
| `success`   | `boolean`       | `true` nếu thành công, `false` nếu lỗi.                               |
| `message`   | `string`        | `"Success"` mặc định, hoặc message lỗi khi `success = false`.         |
| `data`      | `T \| null`     | Payload chính. `null` khi lỗi hoặc response chỉ có message.           |
| `timestamp` | `LocalDateTime` | Thời điểm server trả response (`yyyy-MM-dd'T'HH:mm:ss`).             |

**Error response:**
```json
{
  "success": false,
  "message": "Email or password is incorrect",
  "data": null,
  "timestamp": "2026-02-24T10:30:00"
}
```

> **Quy ước trong tài liệu:** Các response example bên dưới **chỉ hiển thị nội dung của field `data`** để gọn. Khi implement, toàn bộ đều nằm trong `ApiResponse<T>`.  
> Với các endpoint trả message đơn giản (VD: `{ "message": "..." }`), `data` sẽ chứa object đó, VD: `{ "success": true, "message": "Success", "data": { "message": "Password changed successfully" }, "timestamp": "..." }`.

### Financial & Domain Notes (đọc trước khi tích hợp)

- `Wallet.availableBalance = balance - lockedBalance` là giá trị dùng cho kiểm tra khả dụng.
- `FLOAT_MAIN.balance` phải bằng tổng số dư của toàn bộ wallet còn lại (trừ `FLOAT_MAIN`).
- `SYSTEM_TOPUP`, `DEPOSIT`, `WITHDRAW` là boundary operations làm thay đổi `FLOAT_MAIN`.
- Trạng thái request/withdraw/deposit trong tài liệu có thể khác nhãn cũ; khi map logic, ưu tiên enum thực tế trong backend.

---

## 1. COMMON – Dùng chung (mọi role)

> **Convention:**  
> Các `id` trong response đều là `BigInt` auto-increment từ database trừ khi ghi chú khác.  
> Các trường `*Code` (VD: `transactionCode`, `payslipCode`) là mã nghiệp vụ auto-generated ở Backend — không phải Primary Key.  
> Avatar URL là Signed URL từ Cloudinary (Private mode, hết hạn 15 phút), được Backend sinh khi trả response.  
> Mã PIN giao dịch: **5 chữ số** (hash BCrypt lưu trong `user_security_settings.transaction_pin`).  
> **Request Status Enum (7 giá trị):** `PENDING` · `APPROVED_BY_TEAM_LEADER` · `APPROVED_BY_MANAGER` · `APPROVED_BY_CFO` · `PAID` · `REJECTED` · `CANCELLED`.  
> **Request Type Enum (5 giá trị):** `ADVANCE` · `EXPENSE` · `REIMBURSE` · `PROJECT_TOPUP` · `DEPARTMENT_TOPUP`.  
> **Request Action Enum (4 giá trị):** `APPROVE` · `REJECT` · `PAYOUT` · `CANCEL`.

---

### POST `/auth/login`
Đăng nhập, trả về token và thông tin user cơ bản.

**Body:**
```json
{ "email": "string", "password": "string" }
```

**Response (normal login):**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "requiresSetup": false,
  "setupToken": null,
  "user": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "email": "nguyen.van.a@company.com",
    "role": "EMPLOYEE",
    "departmentId": 1,
    "departmentName": "Engineering",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "isFirstLogin": false,
    "status": "ACTIVE"
  }
}
```

**Response (first login - chưa phát access/refresh token):**
```json
{
  "accessToken": null,
  "refreshToken": null,
  "requiresSetup": true,
  "setupToken": "0f5a4f92-8b34-4b53-a1f2-4d4888f3ab8f",
  "user": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "email": "nguyen.van.a@company.com",
    "role": "EMPLOYEE",
    "departmentId": 1,
    "departmentName": "Engineering",
    "avatar": null,
    "isFirstLogin": true,
    "status": "ACTIVE"
  }
}
```
> **DB mapping:** `users` JOIN `roles` (qua `role_id`) JOIN `departments` (qua `department_id`) JOIN `user_profiles` → `file_storages` (qua `avatar_file_id`).  
> `id`: `users.id` (BigInt).  
> `role`: `roles.name` (vd: EMPLOYEE, TEAM_LEADER, MANAGER, ACCOUNTANT, CFO, ADMIN).
> `departmentId` / `departmentName`: nullable nếu chưa gán phòng ban.  
> `avatar`: nullable nếu chưa upload. Signed URL Cloudinary (15 phút).  
> `isFirstLogin`: `users.is_first_login`. Nếu `true` → FE redirect đổi mật khẩu.  
> `status`: `users.status` — `ACTIVE | LOCKED | PENDING`.

---

### GET `/users/stream`
Mở kênh SSE duy nhất sau khi đăng nhập — nhận **tất cả** real-time events của user hiện tại qua một connection.

**Headers:**
- `Authorization: Bearer <accessToken>`
- `Accept: text/event-stream`

**Content-Type response:** `text/event-stream`

**Khi nào client mở:** ngay sau khi đăng nhập thành công và có `accessToken`.

**Event types:**

| Event name | Trigger | Payload |
|---|---|---|
| `connected` | Khi kết nối thành công | `"SSE connected"` (string) |
| `wallet.updated` | Số dư ví thay đổi (deposit, withdraw, nhận lương, v.v.) | `WalletResponse` |
| `transaction.created` | Có giao dịch mới trong ví | `TransactionResponse` |
| `notification` | Có thông báo mới (request approved/rejected, lương, v.v.) | `NotificationDto` |

**Chi tiết từng event:**

---

**`event: connected`**  
Gửi ngay khi kết nối thành công. `data` là plain string.
```text
event: connected
data: SSE connected
```

---

**`event: wallet.updated`**  
Trigger: số dư ví thay đổi sau bất kỳ write operation nào (deposit, withdraw, nhận lương, chuyển khoản, v.v.).  
Payload: `WalletResponse` — snapshot số dư mới nhất.
```text
event: wallet.updated
data: {
  "id": 1,
  "ownerType": "USER",
  "ownerId": 1,
  "balance": 10750000,
  "lockedBalance": 0,
  "availableBalance": 10750000
}
```
> `balance`: tổng số dư. `lockedBalance`: số dư đang bị lock (chờ settle). `availableBalance = balance - lockedBalance`.

---

**`event: transaction.created`**  
Trigger: có giao dịch mới ghi vào ví của user (DEPOSIT, WITHDRAW, PAYSLIP_PAYMENT, ADVANCE_PAYMENT, REVERSAL, v.v.).  
Payload: `TransactionResponse`.
```text
event: transaction.created
data: {
  "id": 102,
  "transactionCode": "TXN-9A33BC21",
  "amount": 500000,
  "type": "DEPOSIT",
  "status": "SUCCESS",
  "referenceType": "DEPOSIT",
  "referenceId": 7,
  "description": "Nap tien qua VNPay - 14081234567890",
  "createdAt": "2026-04-22T10:05:00"
}
```
> `type`: enum `TransactionType` — `DEPOSIT | WITHDRAW | SYSTEM_TOPUP | DEPT_QUOTA_ALLOCATION | PROJECT_QUOTA_ALLOCATION | PAYSLIP_PAYMENT | ADVANCE_PAYMENT | REVERSAL`.  
> `referenceType`: enum `ReferenceType` — `DEPOSIT | WITHDRAWAL | REQUEST | PAYSLIP | SYSTEM`. Nullable.  
> `referenceId`: ID của entity liên quan (DepositLog, WithdrawRequest, Request, Payslip). Nullable.

---

**`event: notification`**  
Trigger: có thông báo mới tạo cho user (request được duyệt/từ chối, lương được chi trả, v.v.).  
Payload: `NotificationDto`.
```text
event: notification
data: {
  "id": 5,
  "type": "REQUEST_APPROVED",
  "title": "Request được duyệt",
  "message": "Request REQ-IT-2604-001 của bạn đã được Team Leader duyệt.",
  "refId": 101,
  "refType": "REQUEST",
  "referenceLink": "/requests/101",
  "isRead": false,
  "createdAt": "2026-04-22T09:30:00"
}
```
> `type`: enum `NotificationType` — `SYSTEM | REQUEST_APPROVED | REQUEST_REJECTED | SALARY_PAID | WARN`.  
> `refType`: `REQUEST | PAYSLIP | PROJECT`. Nullable — `null` với loại `SYSTEM`.  
> `referenceLink`: deep-link để FE navigate đến entity liên quan. Nullable.

**Client implementation (React/TS với `@microsoft/fetch-event-source`):**
```ts
import { fetchEventSource } from "@microsoft/fetch-event-source";

await fetchEventSource("/api/v1/users/stream", {
  method: "GET",
  headers: {
    Authorization: `Bearer ${accessToken}`,
    Accept: "text/event-stream",
  },
  onopen(res) {
    if (!res.ok) throw new Error("Cannot open SSE stream");
  },
  onmessage(msg) {
    switch (msg.event) {
      case "wallet.updated":
        updateWalletBalanceUI(JSON.parse(msg.data));
        break;
      case "transaction.created":
        prependTransactionRow(JSON.parse(msg.data));
        break;
      case "notification":
        showToastAndIncreaseBadge(JSON.parse(msg.data));
        break;
    }
  },
  onerror(err) {
    throw err; // thư viện tự reconnect
  },
});
```

> **Lưu ý:** Chỉ cần mở 1 kết nối duy nhất cho toàn bộ session. Client phân biệt loại event qua `msg.event` và cập nhật đúng phần UI tương ứng. Khi access token hết hạn, đóng stream và mở lại sau khi refresh token.

---

### GET `/users/project/{projectId}/stream`
Mở kênh SSE chuyên dụng cho **Project Wallet** — nhận `wallet.updated` và `transaction.created` khi ví dự án thay đổi. Dành riêng cho **Team Leader**.

**Headers:**
- `Authorization: Bearer <accessToken>`
- `Accept: text/event-stream`

**Path params:** `projectId` (Long, bắt buộc)

**Auth:** `REQUEST_APPROVE_TEAM_LEADER` (Team Leader only)

**Content-Type response:** `text/event-stream`

**Event types:**

| Event name | Trigger | Payload |
|---|---|---|
| `connected` | Khi kết nối thành công | `"SSE connected for wallet PROJECT:{projectId}"` (string) |
| `wallet.updated` | Số dư project wallet thay đổi | `WalletResponse` với `ownerType: "PROJECT"` |
| `transaction.created` | Có giao dịch mới trong project wallet | `LedgerEntryResponse` |

```text
event: wallet.updated
data: {
  "id": 5,
  "ownerType": "PROJECT",
  "ownerId": 1,
  "balance": 95500000,
  "lockedBalance": 5000000,
  "availableBalance": 90500000
}
```

> **Trigger:** Khi Manager approve `PROJECT_TOPUP` (tiền vào project), hoặc Accountant giải ngân `ADVANCE/EXPENSE` (tiền ra project).  
> **Isolation:** Chỉ emitter đã subscribe `PROJECT:{projectId}` nhận event này — không bị pha trộn với `/users/stream`.  
> **Sử dụng:** TL mở 1 kết nối riêng cho từng project dashboard cần theo dõi real-time.

---

### GET `/users/department/{departmentId}/stream`
Mở kênh SSE chuyên dụng cho **Department Wallet** — nhận `wallet.updated` và `transaction.created` khi ví phòng ban thay đổi. Dành riêng cho **Manager**.

**Headers:**
- `Authorization: Bearer <accessToken>`
- `Accept: text/event-stream`

**Path params:** `departmentId` (Long, bắt buộc)

**Auth:** `REQUEST_APPROVE_PROJECT_TOPUP` (Manager only)

**Content-Type response:** `text/event-stream`

**Event types:**

| Event name | Trigger | Payload |
|---|---|---|
| `connected` | Khi kết nối thành công | `"SSE connected for wallet DEPARTMENT:{departmentId}"` (string) |
| `wallet.updated` | Số dư department wallet thay đổi | `WalletResponse` với `ownerType: "DEPARTMENT"` |
| `transaction.created` | Có giao dịch mới trong department wallet | `LedgerEntryResponse` |

```text
event: wallet.updated
data: {
  "id": 2,
  "ownerType": "DEPARTMENT",
  "ownerId": 1,
  "balance": 100000000,
  "lockedBalance": 0,
  "availableBalance": 100000000
}
```

> **Trigger:** Khi CFO approve `DEPARTMENT_TOPUP` (tiền vào), hoặc Manager approve `PROJECT_TOPUP` (tiền ra).  
> **Isolation:** Chỉ emitter đã subscribe `DEPARTMENT:{departmentId}` nhận event này — không bị pha trộn với `/users/stream`.

---

### GET `/users/company-fund/stream`
Mở kênh SSE chuyên dụng cho **Company Fund Wallet** — nhận `wallet.updated` và `transaction.created` khi quỹ công ty thay đổi. Dành cho **CFO** và **Accountant**.

**Headers:**
- `Authorization: Bearer <accessToken>`
- `Accept: text/event-stream`

**Auth:** `WALLET_VIEW_ALL` (CFO, Accountant)

**Content-Type response:** `text/event-stream`

**Event types:**

| Event name | Trigger | Payload |
|---|---|---|
| `connected` | Khi kết nối thành công | `"SSE connected for wallet COMPANY_FUND:1"` (string) |
| `wallet.updated` | Số dư company fund thay đổi | `WalletResponse` với `ownerType: "COMPANY_FUND"` |
| `transaction.created` | Có giao dịch mới trong company fund | `LedgerEntryResponse` |

> **Trigger:** Khi `SYSTEM_TOPUP` (nạp tiền từ ngân hàng, tiền vào), `DEPT_QUOTA_ALLOCATION` (CFO cấp quota phòng ban, tiền ra), hoặc `PAYSLIP_PAYMENT` (chi lương, tiền ra).  
> **Isolation:** Chỉ emitter đã subscribe `COMPANY_FUND:1` nhận event này.

---

### POST `/auth/logout`
Đăng xuất, vô hiệu hoá refresh token.

**Headers:** `Authorization: Bearer <accessToken>`  
**Body:** —  
**Response:** `{ "message": "Logged out successfully" }`

---

### POST `/auth/refresh-token`
Lấy access token mới từ refresh token.

**Headers:** `Authorization: Bearer <accessToken>`  
**Body:**
```json
{ "refreshToken": "eyJhbGci..." }
```
**Response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "requiresSetup": false,
  "setupToken": null,
  "user": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "email": "nguyen.van.a@company.com",
    "role": "EMPLOYEE",
    "departmentId": 1,
    "departmentName": "Engineering",
    "avatar": null,
    "isFirstLogin": false,
    "status": "ACTIVE"
  }
}
```

---

### POST `/auth/forgot-password`
Gửi OTP đặt lại mật khẩu về email (đồng thời nhận mật khẩu mới để xác nhận ở bước OTP).

**Body:**
```json
{
  "email": "string",
  "newPassword": "string",
  "confirmPassword": "string"
}
```
**Response:** `{ "message": "If the email exists, a password reset OTP has been sent" }`

---

### POST `/auth/verify-password-reset`
Xác thực OTP để hoàn tất reset password.

**Body:**
```json
{ "email": "string", "otp": "string" }
```
**Response:** `{ "message": "OTP verified successfully" }`

---

### POST `/auth/first-login/complete`
Hoàn tất first-login setup bằng `setupToken`: đổi mật khẩu + đặt PIN, sau đó trả đầy đủ token.

**Body:**
```json
{
  "setupToken": "string",
  "newPassword": "string",
  "confirmPassword": "string",
  "pin": "12345"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "requiresSetup": null,
  "setupToken": null,
  "user": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "email": "nguyen.van.a@company.com",
    "role": "EMPLOYEE",
    "departmentId": 1,
    "departmentName": "Engineering",
    "avatar": null,
    "isFirstLogin": false,
    "status": "ACTIVE"
  }
}
```

---

### POST `/auth/change-password`
Đổi mật khẩu khi đã đăng nhập.

**Headers:** `Authorization: Bearer <accessToken>`  
**Body:**
```json
{
  "currentPassword": "string",
  "newPassword": "string",
  "confirmNewPassword": "string"
}
```
**Response:** `{ "message": "Password changed successfully" }`

---

### GET `/auth/me`
Lấy thông tin user hiện tại (dùng để restore session khi reload trang).

**Headers:** `Authorization: Bearer <accessToken>`

**Response:**
```json
{
  "id": 1,
  "fullName": "Nguyen Van A",
  "email": "nguyen.van.a@company.com",
  "role": "EMPLOYEE",
  "departmentId": 1,
  "departmentName": "Engineering",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "isFirstLogin": false,
  "status": "ACTIVE"
}
```
> Response giống object `user` trong `POST /auth/login`.

---

### GET `/users/me/profile`
Lấy toàn bộ thông tin profile của user đang đăng nhập.

**DB mapping:** `users` JOIN `user_profiles` (qua `user_id`) JOIN `departments` (qua `department_id`) JOIN `file_storages` (qua `avatar_file_id`) JOIN `user_security_settings` (qua `user_id`).

**Response:**
```json
{
  "id": 1,
  "employeeCode": "MK001",
  "fullName": "Nguyen Van A",
  "email": "nguyen.van.a@company.com",
  "phoneNumber": "+84 901 234 567",
  "dateOfBirth": "1995-06-20",
  "address": "123 Nguyen Trai, Thanh Xuan, Ha Noi",
  "departmentId": 1,
  "departmentName": "Engineering",
  "jobTitle": "Senior Backend Developer",
  "citizenId": "079012345678",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "bankInfo": {
    "bankName": "MB Bank",
    "accountNumber": "0123456789",
    "accountOwner": "NGUYEN VAN A"
  }
}

```
> `id`: `users.id`.  
> `employeeCode`: `user_profiles.employee_code`.  
> `fullName`: `users.full_name`. `email`: `users.email`.  
> `phoneNumber`, `dateOfBirth`, `address`, `citizenId`, `jobTitle`: từ `user_profiles`.  
> `departmentId` / `departmentName`: từ `departments` qua `users.department_id`.  
> `avatar`: Signed URL Cloudinary, qua `user_profiles.avatar_file_id` → `file_storages`.  
> `bankInfo.bankName` = `user_profiles.bank_name`.  
> `bankInfo.accountNumber` = `user_profiles.bank_account_num`.  
> `bankInfo.accountOwner` = `user_profiles.bank_account_owner`.  

---

### PUT `/users/me/profile`
Cập nhật thông tin cá nhân.

**Body:**
```json
{
  "fullName": "string",
  "phoneNumber": "string",
  "dateOfBirth": "string (YYYY-MM-DD, optional)",
  "citizenId": "string (optional)",
  "address": "string (optional)"
}
```
**Response:** _(profile object đầy đủ như GET `/users/me/profile`)_
> `fullName` → cập nhật `users.full_name`.  
> Các trường còn lại → cập nhật `user_profiles`.

---

### GET `/uploads/signature`
Lấy chữ ký upload Cloudinary để client upload trực tiếp (không upload file qua backend).

**Headers:** `Authorization: Bearer <accessToken>`  
**Query params:** `folder` (bắt buộc)

`folder` là enum `UploadFolder`:
- `AVATAR`
- `REQUEST`

Ví dụ request:
`GET /uploads/signature?folder=AVATAR`

**Response:**
```json
{
  "signature": "abc123...",
  "timestamp": 1738900000,
  "apiKey": "123456789",
  "cloudName": "ifms-cloud",
  "folder": "avatars"
}
```
> `folder` trong response là path Cloudinary thực tế (`avatars`, `requests`, ...).  
> Sau khi client upload thành công lên Cloudinary, client gửi metadata file vào API nghiệp vụ tương ứng (ví dụ cập nhật avatar).

---

### PUT `/users/me/avatar`
Cập nhật avatar sau khi upload lên Cloudinary. Backend tạo record `file_storages` và cập nhật `user_profiles.avatar_file_id`. Nếu đã có avatar cũ, xoá file cũ trên Cloudinary và record `file_storages` tương ứng.

**Body:**
```json
{
  "fileName": "profile.jpg",
  "cloudinaryPublicId": "avatars/user_1_1738900000",
  "url": "https://res.cloudinary.com/ifms-cloud/image/upload/v1738900000/avatars/user_1_1738900000.jpg",
  "fileType": "image/jpeg",
  "size": 245000
}
```
**Response:**
```json
{ "avatar": "https://res.cloudinary.com/.../signed..." }
```
> Body chứa thông tin từ Cloudinary upload response → Backend tạo `file_storages` record.  
> Các field bắt buộc theo `FileStorageRequest`: `fileName`, `cloudinaryPublicId`, `url`.  
> Response trả Signed URL (Private mode, 15 phút).

---

### PUT `/users/me/bank-info`
Cập nhật thông tin ngân hàng nhận lương.

**Body:**
```json
{ "bankName": "string", "accountNumber": "string", "accountOwner": "string" }
```
**Response:**
```json
{ "bankName": "MB Bank", "accountNumber": "0123456789", "accountOwner": "NGUYEN VAN A" }
```
> Map: `bankName` → `user_profiles.bank_name`, `accountNumber` → `user_profiles.bank_account_num`, `accountOwner` → `user_profiles.bank_account_owner`.

---

### PUT `/users/me/pin`
Đổi PIN giao dịch (khi đã có PIN).

**Body:**
```json
{ "currentPin": "string", "newPin": "string (5 chữ số)" }
```
**Response:** `{ "message": "PIN updated successfully" }`

---

### POST `/users/me/pin/verify`
Xác minh PIN trước khi thực hiện giao dịch nhạy cảm (rút tiền, trả nợ, giải ngân).

**Body:**
```json
{ "pin": "string" }
```
**Response:** `{ "valid": true }` hoặc `401 Unauthorized`
> Nhập sai PIN → tăng `user_security_settings.retry_count`. Quá 5 lần → set `locked_until = NOW() + 30 phút`.  
> Khi `locked_until` chưa hết hạn → trả `423 Locked`.

---

### GET `/banks`
Danh sách ngân hàng hỗ trợ (dùng cho dropdown chọn ngân hàng). Static data — không map DB.

**Response:**
```json
[
  { "value": "MB Bank",     "label": "MB Bank (Quân đội)" },
  { "value": "Vietcombank", "label": "Vietcombank (VCB)" },
  { "value": "Techcombank", "label": "Techcombank (TCB)" },
  { "value": "BIDV",        "label": "BIDV" },
  { "value": "VietinBank",  "label": "VietinBank" },
  { "value": "ACB",         "label": "ACB (Á Châu)" },
  { "value": "VPBank",      "label": "VPBank (Việt Nam Thịnh Vượng)" },
  { "value": "TPBank",      "label": "TPBank (Tiên Phong)" },
  { "value": "Sacombank",   "label": "Sacombank" },
  { "value": "HDBank",      "label": "HDBank (Phát triển TP.HCM)" }
]
```
> `value` là giá trị lưu vào `user_profiles.bank_name`.

---

### GET `/wallet`
Lấy số dư ví của user hiện tại.

**DB mapping:** `wallets` WHERE `owner_type = 'USER'` AND `owner_id = currentUser.id`.

**Response:**
```json
{
  "id": 1,
  "ownerType": "USER",
  "ownerId": 1,
  "balance": 10250000,
  "lockedBalance": 2000000,
  "availableBalance": 8250000
}
```
> `id`: `wallets.id`.  
> `ownerType`: `wallets.owner_type` (với endpoint này luôn là `USER`).  
> `ownerId`: `wallets.owner_id` (chính là `currentUser.id`).  
> `balance`: `wallets.balance` — tổng số dư ví.  
> `lockedBalance`: `wallets.locked_balance` — số dư đã lock/chờ settle.  
> `availableBalance`: giá trị tính toán `balance - lockedBalance`.

> **Realtime:** Khi số dư thay đổi, server tự push event `wallet.updated` với payload `WalletResponse` qua kênh SSE `GET /users/stream`. Client cập nhật balance UI mà không cần poll lại endpoint này.

---

### GET `/wallet/transactions`
Lịch sử giao dịch ví của user hiện tại, với filter và phân trang.

**Params:** `?from=2026-01-01&to=2026-02-28&page=0&size=20`

**DB mapping:** `ledger_entries` JOIN `transactions` JOIN `wallets` WHERE wallet owner là user hiện tại.

**Response:**
```json
{
  "items": [
    {
      "id": 101,
      "transactionCode": "TXN-8829145A",
      "direction": "CREDIT",
      "amount": 15000000,
      "balanceAfter": 15250000,
      "createdAt": "2026-02-10T09:00:00Z"
    },
    {
      "id": 95,
      "transactionCode": "TXN-6612A33B",
      "direction": "DEBIT",
      "amount": 2000000,
      "balanceAfter": 250000,
      "createdAt": "2026-02-08T14:20:00Z"
    }
  ],
  "total": 42,
  "page": 0,
  "size": 20,
  "totalPages": 3
}
```
> Mỗi `item` map theo `LedgerEntryResponse`: `id`, `transactionCode`, `direction`, `amount`, `balanceAfter`, `createdAt`.  
> `direction`: `DEBIT | CREDIT` (`TransactionDirection`).  
> `amount`: luôn là giá trị tuyệt đối của bút toán; chiều tăng/giảm được xác định bởi `direction`.  
> `balanceAfter`: snapshot số dư wallet ngay sau bút toán.

> **Realtime:** Khi có giao dịch mới, server tự push event `transaction.created` với payload `TransactionResponse` qua kênh SSE `GET /users/stream`. Client prepend row mới vào danh sách mà không cần poll lại endpoint này.

---

### GET `/wallet/transactions/{transactionId}`
Lấy chi tiết một giao dịch theo `transactionId` của user hiện tại.

**Path params:** `transactionId` (Long, bắt buộc)

**DB mapping:** `transactions` JOIN `wallets`, chỉ trả dữ liệu nếu giao dịch thuộc wallet của `currentUser`.

**Response:**
```json
{
  "id": 102,
  "transactionCode": "TXN-9A33BC21",
  "amount": 2000000,
  "type": "WITHDRAW",
  "status": "SUCCESS",
  "referenceType": "WITHDRAWAL",
  "referenceId": 12,
  "description": "Rut tien - VCB20260405103000000001",
  "createdAt": "2026-02-22T10:05:00"
}
```
> Response map theo `TransactionResponse`: `id`, `transactionCode`, `amount`, `type`, `status`, `referenceType`, `referenceId`, `description`, `createdAt`.  
> `type`: enum `TransactionType`.  
> `status`: enum `TransactionStatus`.  
> `referenceType`: enum `ReferenceType`, có thể `null` tùy giao dịch.  
> `referenceId`: ID thực thể nghiệp vụ liên quan, có thể `null`.

---

### POST `/wallet/withdraw`
Rút tiền về tài khoản ngân hàng đã đăng ký. Yêu cầu xác minh PIN (5 chữ số).

**Body:**
```json
{ "amount": 2000000, "userNote": "rut tien thang 8" ,"pin": "string" }
```
**Response:**
```json
{
  "id": 102,
  "withdrawCode": "WD-2026-000012",
  "userId": 1,
  "amount": 2000000,
  "userNote": "Rut tien thang 04",
  "status": "PENDING",
  "accountantNote": null,
  "failureReason": null,
  "createdAt": "2026-02-22T10:05:00",
  "updatedAt": "2026-02-22T10:05:00"
}
```
> Response map theo `WithdrawRequestResponse`: `id`, `withdrawCode`, `userId`, `amount`, `userNote`, `status`, `accountantNote`, `failureReason`, `createdAt`, `updatedAt`.  
> `status`: enum `WithdrawStatus` (`PENDING | COMPLETED | FAILED | REJECTED | CANCELLED`).  
> `accountantNote`, `failureReason` chỉ có giá trị khi request đã được xử lý.

---

### DELETE `/wallet/withdraw/{id}`
Người dùng tự hủy yêu cầu rút tiền của chính mình.

**Path params:** `id` (BigInt, bắt buộc)

**Business rules:**
- Chỉ được hủy request thuộc chính user đang đăng nhập.
- Chỉ hủy được khi `status = PENDING`.

**Response:**
```json
{
  "id": 102,
  "withdrawCode": "WD-2026-000012",
  "userId": 1,
  "amount": 2000000,
  "userNote": "Rut tien thang 04",
  "status": "CANCELLED",
  "accountantNote": null,
  "failureReason": null,
  "createdAt": "2026-02-22T10:05:00",
  "updatedAt": "2026-02-22T10:15:00"
}
```
> Response map theo `WithdrawRequestResponse`: `id`, `withdrawCode`, `userId`, `amount`, `userNote`, `status`, `accountantNote`, `failureReason`, `createdAt`, `updatedAt`.

---

### GET `/wallet/withdraw/my`
Lấy lịch sử rút tiền của user hiện tại (có phân trang).

**Params:** `?page=0&size=10`

**Response:**
```json
{
  "items": [
    {
      "id": 102,
      "withdrawCode": "WD-2026-000012",
      "userId": 1,
      "amount": 2000000,
      "userNote": "Rut tien thang 04",
      "status": "PENDING",
      "accountantNote": null,
      "failureReason": null,
      "createdAt": "2026-02-22T10:05:00",
      "updatedAt": "2026-02-22T10:05:00"
    }
  ],
  "total": 1,
  "page": 0,
  "size": 10,
  "totalPages": 1
}
```
> Endpoint này sử dụng `PageResponse<WithdrawRequestResponse>` theo chuẩn phân trang chung: `items`, `total`, `page`, `size`, `totalPages`.

---

### POST `/wallet/deposit`
Tạo yêu cầu nạp tiền vào ví qua cổng thanh toán.

**Body:**
```json
{ "amount": 500000, "description": "string (optional)" }
```
**Response:**
```json
{
  "gateway": "VNPAY",
  "depositCode": "DEP-2026-000001",
  "transactionRef": "DEP-2026-000001",
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
  "qrCode": null,
  "status": "PENDING",
  "message": "Payment URL generated",
  "expiredAt": "2026-02-22T11:30:00"
}
```
> Response map theo `PaymentResponse`: `gateway`, `depositCode`, `transactionRef`, `paymentUrl`, `qrCode`, `status`, `message`, `expiredAt`.  
> Với VNPay redirect flow, `paymentUrl` là URL chính để FE redirect; `qrCode` có thể `null`.

---

### GET `/projects`
Danh sách projects mà user đang tham gia (dùng populate dropdown khi tạo request).

**Params:** `?status=PLANNING|ACTIVE|PAUSED|CLOSED

**DB mapping:** `projects` JOIN `project_members` WHERE `project_members.user_id = currentUser.id`. Nếu role = MANAGER → lọc theo `projects.department_id`. Nếu ADMIN/ACCOUNTANT → xem toàn bộ tuỳ scope.

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "projectCode": "PRJ-ERP-2026",
      "name": "E-Commerce Platform"
    }
  ]
}
```
> `id`: `projects.id` (BigInt).  
> `projectCode`: `projects.project_code` — auto-generated, format `PRJ-ERP-2026`.  

---

### GET `/projects/:id/phases`
Danh sách phases của một project (dùng populate dropdown chọn phase khi tạo request).

**Params:** `?status=ACTIVE|CLOSED`

**Response:**
```json
{
  "projectId": 1,
  "projectName": "E-Commerce Platform",
  "phases": [
    {
      "id": 2,
      "phaseCode": "PH-UIUX-01",
      "name": "Phase 2: Payment Integration",
      "budgetLimit": 60000000,
      "currentSpent": 54500000
    }
  ]
}
```
> `id`: `project_phases.id` (BigInt).  
> `phaseCode`: `project_phases.phase_code` — auto-generated, format `PH-UIUX-01`.  
> `budgetLimit` / `currentSpent`: `project_phases.budget_limit` / `project_phases.current_spent`.  

---

### GET `/projects/{phaseId}`
Danh sách tất cả danh mục chi tiêu của phase (dùng populate dropdown category khi tạo request theo phase).

**Path params:** `phaseId` (BigInt, bắt buộc)

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "name": "Travel & Accommodation"
    },
    {
      "id": 2,
      "name": "Equipment & Supplies"
    }
  ]
}
```
> Mỗi item chỉ gồm `id`, `name` theo yêu cầu UI dropdown.  
> `id`: `expense_categories.id` (BigInt). `name`: `expense_categories.name`.

---

### GET `/payslips`
Danh sách phiếu lương của user hiện tại.

**Params:** `?year=2025&status=DRAFT|PAID&page=1&limit=12`

**DB mapping:** `payslips` JOIN `payroll_periods` (qua `period_id`) WHERE `payslips.user_id = currentUser.id`.

**Response:**
```json
{
  "items": [
    {
      "id": 10,
      "payslipCode": "PSL-MK001-1025",
      "periodId": 5,
      "periodName": "Lương Tháng 10/2025",
      "month": 10,
      "year": 2025,
      "status": "PAID",
      "finalNetSalary": 22500000
    }
  ],
  "total": 10,
  "page": 1,
  "limit": 12,
  "totalPages": 1
}
```
> `id`: `payslips.id` (BigInt).  
> `payslipCode`: `payslips.payslip_code` — auto-generated, format `PSL-EMP001-0226`.  
> `periodId`: `payslips.period_id`. `periodName`: `payroll_periods.name`.  
> `month` / `year`: `payroll_periods.month` / `payroll_periods.year`.  
> `status`: `payslips.status` — `DRAFT | PAID`.  
> `finalNetSalary`: `payslips.final_net_salary`.

---

### GET `/payslips/:id`
Chi tiết phiếu lương.

**DB mapping:** `payslips` JOIN `payroll_periods` JOIN `users` JOIN `user_profiles` JOIN `departments`.

**Response:**
```json
{
  "id": 10,
  "payslipCode": "PSL-MK001-1025",
  "periodId": 5,
  "periodName": "Lương Tháng 10/2025",
  "month": 10,
  "year": 2025,
  "status": "PAID",
  "baseSalary": 20000000,
  "bonus": 5000000,
  "allowance": 2000000,
  "totalEarnings": 27000000,
  "deduction": 4000000,
  "advanceDeduct": 500000,
  "totalDeduction": 4500000,
  "finalNetSalary": 22500000,
  "employee": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "employeeCode": "MK001",
    "departmentName": "Engineering",
    "jobTitle": "Senior Backend Developer",
    "bankName": "MB Bank",
    "bankAccountNum": "****6789"
  }
}
```
> `baseSalary`: `payslips.base_salary`.  
> `bonus`: `payslips.bonus`. `allowance`: `payslips.allowance`.  
> `deduction`: `payslips.deduction`. `advanceDeduct`: `payslips.advance_deduct`.  
> `totalEarnings = baseSalary + bonus + allowance` (computed).  
> `totalDeduction = deduction + advanceDeduct` (computed).  
> `finalNetSalary = totalEarnings - totalDeduction` = `payslips.final_net_salary`.  
> `employee.bankAccountNum`: chỉ trả 4 số cuối (masked) từ `user_profiles.bank_account_num`.  
> `employee`: join từ `users` + `user_profiles` + `departments`.

---

### GET `/notifications`
Danh sách thông báo của user hiện tại.

**Params:** `?isRead=true|false&type=SYSTEM|REQUEST_APPROVED|REQUEST_REJECTED|SALARY_PAID|WARN&page=1&limit=20`

**DB mapping:** `notifications` WHERE `user_id = currentUser.id`.

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "type": "REQUEST_APPROVED",
      "title": "Request Approved",
      "message": "Your request REQ-IT-2602-001 has been approved by manager",
      "isRead": false,
      "refId": 101,
      "refType": "REQUEST",
      "createdAt": "2026-02-19T10:30:00Z"
    }
  ],
  "unreadCount": 3,
  "total": 15,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `id`: `notifications.id` (BigInt).  
> `type`: `notifications.type` — `SYSTEM | REQUEST_APPROVED | REQUEST_REJECTED | SALARY_PAID | WARN`.
> `title`: `notifications.title`. `message`: `notifications.message`.  
> `isRead`: `notifications.is_read`.  
> `refId`: `notifications.ref_id` — ID đối tượng liên quan (BigInt). Nullable.  
> `refType`: `notifications.ref_type` — `REQUEST | PAYSLIP | PROJECT`. Nullable.  
> `unreadCount`: COUNT WHERE `is_read = false` — cho badge notification.

> **Realtime:** Khi có thông báo mới, server tự push event `notification` với payload `NotificationDto` qua kênh SSE `GET /users/stream`. Client hiện toast + tăng badge số chưa đọc mà không cần poll lại endpoint này.

---

### PATCH `/notifications/{id}/read`
Đánh dấu một thông báo đã đọc.

**Response:**
```json
{
  "id": 1,
  "type": "REQUEST_SUBMITTED",
  "title": "Request moi can duyet",
  "message": "Ban co mot request moi can xu ly.",
  "refId": 101,
  "refType": "REQUEST",
  "referenceLink": "/requests/101",
  "isRead": true,
  "createdAt": "2026-04-06T09:30:00"
}
```
> Response map theo `NotificationDto`.

---

### PATCH `/notifications/read-all`
Đánh dấu tất cả thông báo đã đọc.

**Response:** `null`
> Endpoint trả `ApiResponse<Void>` nên `data = null`.

---

## 2. EMPLOYEE

---

### GET `/requests`
Danh sách requests của employee hiện tại.

**Params:** `?type=ADVANCE|EXPENSE|REIMBURSE|PROJECT_TOPUP|DEPARTMENT_TOPUP&status=PENDING|APPROVED_BY_TEAM_LEADER|APPROVED_BY_MANAGER|APPROVED_BY_CFO|PAID|REJECTED|CANCELLED&search=string&page=1&limit=20`

**DB mapping:** `requests` WHERE `requester_id = currentUser.id` JOIN `projects` JOIN `project_phases` LEFT JOIN `expense_categories`.

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "items": [
      {
        "id": 101,
        "requestCode": "REQ-IT-2602-001",
        "type": "ADVANCE",
        "status": "PENDING",
        "amount": 5000000,
        "approvedAmount": null,
        "description": "Advance payment for development team travel expenses",
        "rejectReason": null,
        "projectId": 1,
        "projectName": "E-Commerce Platform",
        "phaseId": 2,
        "phaseName": "Phase 2: Payment Integration",
        "categoryId": 1,
        "categoryName": "Travel & Accommodation",
        "createdAt": "2026-02-15T09:30:00",
        "updatedAt": "2026-02-15T09:30:00"
      }
    ],
    "total": 24,
    "page": 1,
    "size": 20,
    "totalPages": 2
  },
  "timestamp": "2026-04-24T15:30:00"
}
```
> `id`: `requests.id` (Long).  
> `requestCode`: `requests.request_code` — auto-generated, format `REQ-{DEPT}-{MMYY}-{SEQ}`.  
> `type`: `requests.type` — `ADVANCE | EXPENSE | REIMBURSE | PROJECT_TOPUP | DEPARTMENT_TOPUP`.  
> `status`: `requests.status` — `PENDING | APPROVED_BY_TEAM_LEADER | APPROVED_BY_MANAGER | APPROVED_BY_CFO | PAID | REJECTED | CANCELLED`.  
> `amount` / `approvedAmount`: `requests.amount` / `requests.approved_amount`. `approvedAmount` nullable nếu chưa duyệt.  
> `rejectReason`: `requests.reject_reason`. Nullable.  
> `projectId` / `projectName`: join `projects`. Nullable cho `DEPARTMENT_TOPUP`.  
> `phaseId` / `phaseName`: join `project_phases`. Nullable cho `PROJECT_TOPUP`/`DEPARTMENT_TOPUP`.  
> `categoryId` / `categoryName`: join `expense_categories`. Nullable cho `PROJECT_TOPUP`/`DEPARTMENT_TOPUP`.  
> `createdAt` / `updatedAt`: từ `BaseEntity`.

---

### GET `/requests/summary`
Tổng hợp số lượng request theo trạng thái của user hiện tại, response thay đổi theo role.

**Response (EMPLOYEE):**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "totalPendingApproval": 2,
    "totalApproved": 12,
    "totalRejected": 2,
    "totalPaid": 8,
    "totalCancelled": 1
  },
  "timestamp": "2026-04-24T15:30:00"
}
```

**Response (TEAM_LEADER):**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "totalPendingManagerApproval": 2,
    "totalApproved": 12,
    "totalRejected": 2,
    "totalPaid": 8,
    "totalCancelled": 1
  },
  "timestamp": "2026-04-24T15:30:00"
}
```

**Response (MANAGER):**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "totalPendingCfoApproval": 2,
    "totalApproved": 12,
    "totalRejected": 2,
    "totalPaid": 8,
    "totalCancelled": 1
  },
  "timestamp": "2026-04-24T15:30:00"
}
```

> Computed: COUNT GROUP BY `requests.status` WHERE `requester_id = currentUser.id`, sau đó map field theo role.

---

### GET `/requests/{id}`
Chi tiết một request (employee chỉ xem request của mình).

**DB mapping:** `requests` JOIN `projects` JOIN `project_phases` LEFT JOIN `expense_categories` + sub-query `request_attachments` → `file_storages` + sub-query `request_histories`.

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 101,
    "requestCode": "REQ-IT-2602-001",
    "type": "ADVANCE",
    "status": "PENDING",
    "amount": 5000000,
    "approvedAmount": null,
    "description": "Advance payment for development team travel expenses",
    "rejectReason": null,
    "paidAt": null,
    "projectId": 1,
    "projectCode": "PRJ-ERP-2026",
    "projectName": "E-Commerce Platform",
    "phaseId": 2,
    "phaseCode": "PH-PAY-01",
    "phaseName": "Phase 2: Payment Integration",
    "categoryId": 1,
    "categoryName": "Travel & Accommodation",
    "advanceBalanceId": null,
    "requesterId": 1,
    "requesterName": "Nguyen Van A",
    "attachments": [
      {
        "fileId": 10,
        "fileName": "Travel_Itinerary.pdf",
        "cloudinaryPublicId": "requests/file_adv_001",
        "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_adv_001.pdf",
        "fileType": "application/pdf",
        "size": 156789
      }
    ],
    "timeline": [
      {
        "id": 1,
        "action": "APPROVE",
        "statusAfterAction": "APPROVED_BY_TEAM_LEADER",
        "actorId": 8,
        "actorName": "Le Van Minh",
        "comment": "Approved",
        "createdAt": "2026-02-16T10:30:00"
      }
    ],
    "createdAt": "2026-02-15T09:30:00",
    "updatedAt": "2026-02-15T09:30:00"
  },
  "timestamp": "2026-04-24T15:30:00"
}
```
> `attachments[]`: join `request_attachments` → `file_storages`. `url` trả theo giá trị lưu DB (đã chuyển qua public URL, không signed bắt buộc).  
> `timeline[]`: từ `request_histories`. `action`: `RequestAction` enum — `APPROVE | REJECT | PAYOUT | CANCEL`.  
> `statusAfterAction`: Snapshot trạng thái Request SAU khi action. Values: `PENDING | APPROVED_BY_TEAM_LEADER | APPROVED_BY_MANAGER | APPROVED_BY_CFO | PAID | REJECTED | CANCELLED`.  
> `actorId` / `actorName`: join `users` qua `request_histories.actor_id`.  
> `categoryId` / `categoryName`: BẮT BUỘC cho ADVANCE/EXPENSE/REIMBURSE. NULL cho PROJECT_TOPUP/DEPARTMENT_TOPUP.

---

### POST `/requests`
Tạo request mới. Backend auto-generate `requestCode`. Trạng thái khởi tạo là `PENDING`.

> **Flow 1 (ADVANCE / EXPENSE / REIMBURSE):** cần `projectId`, `phaseId`, `categoryId`. `EXPENSE` và `REIMBURSE` bắt buộc có chứng từ đính kèm.  
> **Flow 2 (PROJECT_TOPUP):** cần `projectId`; `phaseId`/`categoryId` = `null`.  
> **Flow 3 (DEPARTMENT_TOPUP):** `projectId`/`phaseId`/`categoryId` = `null`.

**Body (Flow 1 - ADVANCE/EXPENSE):**
```json
{
  "type": "ADVANCE",
  "projectId": 1,
  "phaseId": 2,
  "categoryId": 1,
  "amount": 5000000,
  "description": "Advance for development tools Q1",
  "attachments": [
    {
      "fileName": "tool_invoice.pdf",
      "cloudinaryPublicId": "requests/file_tool_001",
      "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_tool_001.pdf",
      "fileType": "application/pdf",
      "size": 120000
    }
  ]
}
```

**Body (Flow 1 - REIMBURSE):**
```json
{
  "type": "REIMBURSE",
  "projectId": 1,
  "phaseId": 2,
  "categoryId": 1,
  "advanceBalanceId": 15,
  "amount": 3500000,
  "description": "Quyet toan tam ung dot 1",
  "attachments": [
    {
      "fileName": "invoice-04-2026.pdf",
      "cloudinaryPublicId": "documents/abc_xyz_123",
      "url": "https://res.cloudinary.com/.../upload/v1738900000/documents/abc_xyz_123.pdf",
      "fileType": "application/pdf",
      "size": 245000
    }
  ]
}
```

**Body (Flow 2 - PROJECT_TOPUP):**
```json
{
  "type": "PROJECT_TOPUP",
  "projectId": 1,
  "amount": 50000000,
  "description": "Xin cấp thêm vốn cho Phase 2"
}
```

**Body (Flow 3 - DEPARTMENT_TOPUP):**
```json
{
  "type": "DEPARTMENT_TOPUP",
  "amount": 200000000,
  "description": "Xin cấp vốn Q1/2026 cho phòng Engineering"
}
```

> `type`: `ADVANCE | EXPENSE | REIMBURSE | PROJECT_TOPUP | DEPARTMENT_TOPUP`.  
> `advanceBalanceId`: chỉ dùng cho `REIMBURSE`; các type khác để `null`.  
> `attachments`: danh sách object theo `FileStorageRequest` gồm `fileName`, `cloudinaryPublicId`, `url` (bắt buộc), `fileType`, `size`.  
> `amount` phải dương; validate quyền tạo request và ngân sách khả dụng theo từng flow.

**Status transition sau khi tạo:**
- Bắt đầu: `PENDING`
- Flow 1: `PENDING -> APPROVED_BY_TEAM_LEADER -> PAID`
- Flow 2: `PENDING -> APPROVED_BY_MANAGER -> PAID` (đồng bộ trong cùng transaction — không qua scheduler)
- Flow 3: `PENDING -> APPROVED_BY_CFO -> PAID` (scheduler auto-pay)
- Terminal states: `REJECTED`, `CANCELLED`

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 102,
    "requestCode": "REQ-IT-2602-002",
    "type": "ADVANCE",
    "status": "PENDING",
    "amount": 5000000,
    "approvedAmount": null,
    "rejectReason": null,
    "description": "Advance for development tools Q1",
    "paidAt": null,
    "projectId": 1,
    "projectCode": "PRJ-ERP-2026",
    "projectName": "E-Commerce Platform",
    "phaseId": 2,
    "phaseCode": "PH-PAY-01",
    "phaseName": "Phase 2: Payment Integration",
    "categoryId": 1,
    "categoryName": "Travel & Accommodation",
    "advanceBalanceId": null,
    "requesterId": 1,
    "requesterName": "Nguyen Van A",
    "attachments": [
      {
        "fileId": 10,
        "fileName": "tool_invoice.pdf",
        "cloudinaryPublicId": "requests/file_tool_001",
        "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_tool_001.pdf",
        "fileType": "application/pdf",
        "size": 120000
      }
    ],
    "timeline": [],
    "createdAt": "2026-02-24T09:00:00",
    "updatedAt": "2026-02-24T09:00:00"
  },
  "timestamp": "2026-04-24T15:30:00"
}
```

> Response bám theo domain model `Request`: `id`, `requestCode`, `type`, `status`, `amount`, `approvedAmount`, `rejectReason`, `description`, `paidAt`, các FK liên quan (`projectId`, `phaseId`, `categoryId`, `advanceBalanceId`), `attachments`, `timeline`, `createdAt`, `updatedAt`.
> HTTP status thực tế: `201 Created`.

---

### PUT `/requests/{id}`
Chỉnh sửa request. Chỉ cho phép khi `status = PENDING`. Chỉ owner (requester) được sửa.

**Body:**
```json
{
  "amount": 5000000,
  "description": "Updated description",
  "attachments": [
    {
      "fileName": "Travel_Itinerary.pdf",
      "cloudinaryPublicId": "requests/file_adv_001",
      "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_adv_001.pdf",
      "fileType": "application/pdf",
      "size": 156789
    },
    {
      "fileName": "Receipt_updated.jpg",
      "cloudinaryPublicId": "requests/file_adv_012",
      "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_adv_012.jpg",
      "fileType": "image/jpeg",
      "size": 89000
    }
  ]
}
```
> `attachments`: ghi đè (sync) — danh sách mới thay thế hoàn toàn danh sách cũ trong `request_attachments`. Các phần tử phải theo cấu trúc `FileStorageRequest`.

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 101,
    "requestCode": "REQ-IT-2602-001",
    "type": "ADVANCE",
    "status": "PENDING",
    "amount": 5000000,
    "approvedAmount": null,
    "description": "Updated description",
    "rejectReason": null,
    "paidAt": null,
    "projectId": 1,
    "projectCode": "PRJ-ERP-2026",
    "projectName": "E-Commerce Platform",
    "phaseId": 2,
    "phaseCode": "PH-PAY-01",
    "phaseName": "Phase 2: Payment Integration",
    "categoryId": 1,
    "categoryName": "Travel & Accommodation",
    "advanceBalanceId": null,
    "requesterId": 1,
    "requesterName": "Nguyen Van A",
    "attachments": [
      {
        "fileId": 10,
        "fileName": "Travel_Itinerary.pdf",
        "cloudinaryPublicId": "requests/file_adv_001",
        "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_adv_001.pdf",
        "fileType": "application/pdf",
        "size": 156789
      },
      {
        "fileId": 12,
        "fileName": "Receipt_updated.jpg",
        "cloudinaryPublicId": "requests/file_adv_012",
        "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/file_adv_012.jpg",
        "fileType": "image/jpeg",
        "size": 89000
      }
    ],
    "timeline": [],
    "createdAt": "2026-02-15T09:30:00",
    "updatedAt": "2026-02-24T11:00:00"
  },
  "timestamp": "2026-04-24T15:30:00"
}
```

---

### DELETE `/requests/{id}`
Huỷ request (chuyển status sang `CANCELLED`). Chỉ owner được huỷ và chỉ cho phép khi `status = PENDING`.

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "message": "Request cancelled successfully"
  },
  "timestamp": "2026-04-24T15:30:00"
}
```
> Backend cập nhật `requests.status = CANCELLED`. Tạo `request_histories`: `action = CANCEL`, `status_after_action = CANCELLED`.

---

## 3. TEAM_LEADER

> **Vai trò:** Quản lý nội bộ dự án — thêm Member, chia Phase/Category Budget, duyệt MỌI chi tiêu Member (Luồng 1).  
> Team Leader được Manager chỉ định khi tạo project (`project_members.project_role = LEADER`).  
> Một user có thể là LEADER của nhiều project.

---

### 3.1 Quản lý Dự án (Project Setup & Members)

> Team Leader chịu trách nhiệm: thêm/xóa Members, tạo Phase, gán Category Budget. Manager chỉ tạo project shell + chỉ định Team Leader.

---

### GET `/team-leader/projects`
Danh sách projects mà user hiện tại là LEADER.

**Params:** `?status=PLANNING|ACTIVE|PAUSED|CLOSED&search=string&page=1&limit=20`


**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "projectCode": "PRJ-ERP-2026",
      "name": "ERP Integration",
      "status": "ACTIVE",
      "totalBudget": 150000000,
      "availableBudget": 95500000,
      "totalSpent": 54500000,
      "memberCount": 5,
      "currentPhaseId": 2,
      "currentPhaseName": "Phase 2: Development",
      "createdAt": "2026-01-05T09:00:00"
    }
  ],
  "total": 2,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```

---

### GET `/team-leader/projects/:id`
Chi tiết project mà user là LEADER — bao gồm phases, members, budget overview.

**DB mapping:** `projects` WHERE `id = :id` AND currentUser is LEADER. JOIN `project_phases` + `project_members` → `users` → `user_profiles`.

**Response:**
```json
{
  "id": 1,
  "projectCode": "PRJ-ERP-2026",
  "name": "ERP Integration",
  "description": "Full ERP integration with microservices and API layer.",
  "status": "ACTIVE",
  "totalBudget": 150000000,
  "availableBudget": 95500000,
  "totalSpent": 54500000,
  "departmentId": 1,
  "managerId": 5,
  "currentPhaseId": 2,
  "phases": [
    {
      "id": 2,
      "phaseCode": "PH-DEV-02",
      "name": "Phase 2: Payment Integration",
      "budgetLimit": 60000000,
      "currentSpent": 54500000,
      "status": "ACTIVE",
      "startDate": "2026-01-01",
      "endDate": "2026-03-31"
    }
  ],
  "members": [
    {
      "userId": 8,
      "fullName": "Le Van Minh",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "employeeCode": "MK008",
      "projectRole": "LEADER",
      "position": "Team Leader",
      "joinedAt": "2026-01-05T09:00:00"
    },
    {
      "userId": 1,
      "fullName": "Nguyen Van An",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "employeeCode": "MK001",
      "projectRole": "MEMBER",
      "position": "Backend Developer",
      "joinedAt": "2026-01-10T09:00:00"
    }
  ],
  "createdAt": "2026-01-05T09:00:00",
  "updatedAt": "2026-02-20T14:00:00"
}
```

---

### POST `/team-leader/projects/:id/members`
Thêm member vào project. Team Leader chọn user từ cùng department và gán `position`.

**Body:**
```json
{
  "userId": 3,
  "position": "Tester"
}
```
> `userId`: `users.id` — phải thuộc cùng `department_id` với project, `status = ACTIVE`, chưa là member của project.  
> `position`: String free text — chức danh hiển thị (VD: "Backend Dev", "Tester", "BA", "AI Engineer").  
> Backend auto-set `project_role = MEMBER`. Team Leader **KHÔNG** thể thêm LEADER khác (chỉ Manager đổi được LEADER qua `PUT /manager/projects/:id`).

**Response:**
```json
{
  "userId": 3,
  "fullName": "Pham Thi Lan",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "employeeCode": "MK003",
  "projectRole": "MEMBER",
  "position": "Tester",
  "joinedAt": "2026-02-24T10:00:00"
}
```

---

### PUT `/team-leader/projects/:id/members/:userId`
Cập nhật `position` của member trong project.

**Body:**
```json
{
  "position": "Senior Tester"
}
```
> Chỉ cho phép sửa `position`. KHÔNG cho phép sửa `projectRole` (chỉ Manager đổi LEADER).

**Response:**
```json
{
  "userId": 3,
  "fullName": "Pham Thi Lan",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "employeeCode": "MK003",
  "projectRole": "MEMBER",
  "position": "Senior Tester",
  "joinedAt": "2026-02-24T10:00:00"
}
```

---

### DELETE `/team-leader/projects/:id/members/:userId`
Xóa member khỏi project.

> **Validation:** KHÔNG cho phép xóa chính mình (LEADER). KHÔNG cho phép xóa member đang có request `PENDING` hoặc `APPROVED_BY_TEAM_LEADER` trong project này.

**Response:** `{ "message": "Member removed from project successfully" }`

---

### GET `/team-leader/projects/:id/available-members`
Danh sách users trong cùng department **chưa tham gia** project này — dùng cho dropdown khi thêm member.

**Params:** `?search=string`

**Response:**
```json
[
  {
    "id": 3,
    "fullName": "Pham Thi Lan",
    "employeeCode": "MK003",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "email": "pham.lan@ifms.vn",
    "jobTitle": "QA Engineer"
  },
  {
    "id": 6,
    "fullName": "Vo Thanh Hung",
    "employeeCode": "MK006",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "email": "vo.hung@ifms.vn",
    "jobTitle": "Designer"
  }
]
```

---

### POST `/team-leader/projects/:id/phases`
Thêm phase mới vào project. Backend auto-generate `phaseCode`.

**Body:**
```json
{
  "name": "Phase 3: Testing",
  "budgetLimit": 50000000,
  "startDate": "2026-06-01",
  "endDate": "2026-08-31"
}
```
> Validation: `SUM(all_phases.budgetLimit) + newPhase.budgetLimit ≤ projects.available_budget`. Phase mới `status = ACTIVE`.

**Response:**
```json
{
  "id": 5,
  "phaseCode": "PH-TEST-03",
  "name": "Phase 3: Testing",
  "budgetLimit": 50000000,
  "currentSpent": 0,
  "status": "ACTIVE",
  "startDate": "2026-06-01",
  "endDate": "2026-08-31"
}
```

---

### PUT `/team-leader/projects/:id/phases/:phaseId`
Cập nhật thông tin phase.

**Body:**
```json
{
  "name": "string (optional)",
  "budgetLimit": 60000000,
  "endDate": "2026-09-30",
  "status": "ACTIVE"
}
```
> `status`: `ACTIVE | CLOSED`. Khi `CLOSED` → chặn tạo request cho phase này.  
> `budgetLimit`: chỉ cho phép tăng nếu còn available_budget, hoặc giảm nếu `budgetLimit >= currentSpent`.

**Response:**
```json
{
  "id": 2,
  "phaseCode": "PH-DEV-02",
  "name": "Phase 2: Payment Integration",
  "budgetLimit": 60000000,
  "currentSpent": 54500000,
  "status": "ACTIVE",
  "startDate": "2026-01-01",
  "endDate": "2026-09-30"
}
```


### 3.2 Quản lý Team Members (Team Overview)

> Team Leader xem tổng quan thành viên trong các dự án mình quản lý — theo dõi chi tiêu, debt, requests đang pending.

---

### GET `/team-leader/team-members`
Danh sách tất cả members thuộc các projects mà user là LEADER (gộp từ tất cả projects, deduplicate theo userId).

**Params:** `?projectId=1&search=string&page=1&limit=20`

**DB mapping:** `project_members` WHERE `project_id IN (projects where currentUser is LEADER)` JOIN `users` + `user_profiles` + `wallets` (debt_balance) + aggregate `requests`.

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "fullName": "Nguyen Van An",
      "email": "van.an@ifms.vn",
      "employeeCode": "MK001",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "jobTitle": "Backend Developer",
      "status": "ACTIVE",
      "debtBalance": 8500000,
      "pendingRequestsCount": 2,
      "projects": [
        {
          "projectId": 1,
          "projectCode": "PRJ-ERP-2026",
          "projectName": "ERP Integration",
          "position": "Backend Developer"
        }
      ]
    },
    {
      "id": 3,
      "fullName": "Pham Thi Lan",
      "email": "pham.lan@ifms.vn",
      "employeeCode": "MK003",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "jobTitle": "QA Engineer",
      "status": "ACTIVE",
      "debtBalance": 0,
      "pendingRequestsCount": 0,
      "projects": [
        {
          "projectId": 1,
          "projectCode": "PRJ-ERP-2026",
          "projectName": "ERP Integration",
          "position": "Tester"
        },
        {
          "projectId": 3,
          "projectCode": "PRJ-MOB-2026",
          "projectName": "Mobile App",
          "position": "QA Lead"
        }
      ]
    }
  ],
  "total": 8,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `debtBalance`: `wallets.debt_balance`.  
> `pendingRequestsCount`: COUNT `requests` WHERE `requester_id = user.id` AND `project_id IN (LEADER projects)` AND `status IN (PENDING, APPROVED_BY_TEAM_LEADER)`.  
> `projects[]`: danh sách projects mà member tham gia (chỉ lọc projects do TL này quản lý).  
> Nếu filter `projectId` → chỉ hiển thị members của project đó.

---

### GET `/team-leader/team-members/:userId`
Chi tiết một team member — bao gồm danh sách projects, recent requests trong scope của Team Leader.

**Response:**
```json
{
  "id": 1,
  "fullName": "Nguyen Van An",
  "email": "van.an@ifms.vn",
  "employeeCode": "MK001",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "jobTitle": "Backend Developer",
  "phoneNumber": "+84 901 234 567",
  "status": "ACTIVE",
  "debtBalance": 8500000,
  "pendingRequestsCount": 2,
  "projects": [
    {
      "projectId": 1,
      "projectCode": "PRJ-ERP-2026",
      "projectName": "ERP Integration",
      "position": "Backend Developer",
      "joinedAt": "2026-01-10T09:00:00"
    }
  ],
  "recentRequests": [
    {
      "id": 101,
      "requestCode": "REQ-IT-2602-001",
      "type": "ADVANCE",
      "amount": 5000000,
      "status": "PENDING",
      "projectCode": "PRJ-ERP-2026",
      "categoryName": "Equipment & Software",
      "createdAt": "2026-02-18T09:15:00"
    }
  ]
}
```
> `recentRequests`: top 10 gần nhất từ `requests` WHERE `requester_id = :userId` AND `project_id IN (LEADER projects)`.

---

### 3.3 Duyệt Request (Approval Flow — Luồng 1)

---

### GET `/team-leader/approvals`
Danh sách requests chi tiêu chờ Team Leader quyết định ở **Flow 1** (`ADVANCE | EXPENSE | REIMBURSE`).

**Params:** `?type=ADVANCE|EXPENSE|REIMBURSE&projectId=1&search=string&page=0&size=20`

**DB mapping:**
- `requests.status = PENDING`
- `requests.type IN (ADVANCE, EXPENSE, REIMBURSE)`
- `requests.project_id` thuộc các project mà current user là LEADER (`project_members.project_role = LEADER`)
- Join `users`, `projects`, `project_phases`, `expense_categories`, `request_attachments -> file_storages`

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "items": [
      {
        "id": 101,
        "requestCode": "REQ-IT-2602-001",
        "type": "ADVANCE",
        "amount": 5000000,
        "requester": {
          "id": 1,
          "fullName": "Nguyen Van An",
          "avatar": "https://res.cloudinary.com/.../upload/v1738900000/avatar/mk001.jpg",
          "employeeCode": "MK001"
        },
        "project": {
          "id": 1,
          "projectCode": "PRJ-ERP-2026"
        },
        "phase": {
          "id": 2,
          "phaseCode": "PH-DEV-02"
        },
        "categoryId": 1,
        "categoryName": "Equipment & Software",
        "createdAt": "2026-02-18T09:15:00"
      }
    ],
    "total": 7,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-04-24T15:30:00"
}
```

> Team Leader chỉ làm **decision** (approve/reject), không execute payout.

---

### GET `/team-leader/approvals/{id}`
Chi tiết một request chi tiêu cần Team Leader duyệt.

**Response:** 
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 101,
    "requestCode": "REQ-IT-2602-001",
    "type": "ADVANCE",
    "status": "PENDING",
    "amount": 5000000,
    "description": "Advance payment for API licenses.",
    "requester": {
      "id": 1,
      "fullName": "Nguyen Van An",
      "avatar": "https://res.cloudinary.com/.../upload/v1738900000/avatar/mk001.jpg",
      "employeeCode": "MK001",
      "jobTitle": "Backend Developer",
      "email": "van.an@ifms.vn"
    },
    "project": {
      "id": 1,
      "projectCode": "PRJ-ERP-2026",
      "name": "ERP Integration"
    },
    "phase": {
      "id": 2,
      "phaseCode": "PH-DEV-02",
      "name": "Phase 2 - Development",
      "budgetLimit": 80000000,
      "currentSpent": 62500000
    },
    "categoryId": 1,
    "categoryName": "Equipment & Software",
    "attachments": [
      {
        "fileId": 31,
        "fileName": "stripe_invoice_Q1.pdf",
        "cloudinaryPublicId": "requests/stripe_invoice_q1",
        "url": "https://res.cloudinary.com/.../upload/v1738900000/requests/stripe_invoice_q1.pdf",
        "fileType": "application/pdf",
        "size": 251000
      }
    ],
    "createdAt": "2026-02-18T09:15:00"
  },
  "timestamp": "2026-04-24T15:30:00"
}
```


Giống `GET /requests/{id}` nhưng trong scope project của Team Leader và dùng trạng thái Flow 1 (`PENDING`, `APPROVED_BY_TEAM_LEADER`, `PAID`, `REJECTED`, `CANCELLED`).

---

### POST `/team-leader/approvals/{id}/approve`
Team Leader duyệt request chi tiêu (Flow 1).

> Sau khi duyệt: request đi vào bước Accountant execution theo SoD của Flow 1.

**Body:**
```json
{ "comment": "string (optional)", "approvedAmount": 5000000 }
```
> `approvedAmount`: optional — nếu không gửi, mặc định = `requests.amount`. Nếu gửi, phải ≤ `amount`.

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 101,
    "requestCode": "REQ-IT-2602-001",
    "status": "APPROVED_BY_TEAM_LEADER",
    "approvedAmount": 5000000,
    "comment": "Approved - forward to accountant."
  },
  "timestamp": "2026-04-24T15:30:00"
}
```
> Backend tạo `request_histories` với `action = APPROVE`, `status_after_action = APPROVED_BY_TEAM_LEADER`. Request chuyển sang trạng thái `APPROVED_BY_TEAM_LEADER`, chờ Accountant giải ngân.

---

### POST `/team-leader/approvals/{id}/reject`
Team Leader từ chối request. Bắt buộc nhập lý do.

**Body:**
```json
{ "reason": "string" }
```
**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 101,
    "requestCode": "REQ-IT-2602-001",
    "status": "REJECTED",
    "rejectReason": "Category budget insufficient for this phase"
  },
  "timestamp": "2026-04-24T15:30:00"
}
```
> Backend cập nhật `requests.status = REJECTED`, `requests.reject_reason`. Tạo `request_histories`: `action = REJECT`, `status_after_action = REJECTED`.

> Flow 1 recap: `PENDING -> APPROVED_BY_TEAM_LEADER -> PAID` hoặc `REJECTED`.

> **Realtime — Project Wallet:** Team Leader có thể subscribe kênh SSE `GET /users/project/{projectId}/stream` để nhận `wallet.updated` và `transaction.created` khi ví dự án thay đổi (tiền vào khi Manager approve PROJECT_TOPUP, tiền ra khi Accountant giải ngân ADVANCE/EXPENSE).

---

### 3.4 Quản lý Category Budget

---

### GET `/team-leader/projects/:id/categories`
Danh sách expense categories đã gán budget cho một phase trong dự án. Dùng để quản lý Category Budget.

**Params:** `?phaseId=2`

**Response:**
```json
{
  "projectId": 1,
  "phaseId": 2,
  "phaseName": "Phase 2: Payment Integration",
  "categories": [
    {
      "categoryId": 1,
      "categoryName": "Travel & Accommodation",
      "budgetLimit": 20000000,
      "currentSpent": 5000000,
      "remaining": 15000000
    },
    {
      "categoryId": 2,
      "categoryName": "Equipment & Software",
      "budgetLimit": 40000000,
      "currentSpent": 30000000,
      "remaining": 10000000
    }
  ]
}
```
> `remaining`: computed `budgetLimit - currentSpent`.  
> Source: `phase_category_budgets` WHERE `phase_id = :phaseId`.

---

### PUT `/team-leader/projects/:id/categories`
Team Leader cập nhật **1** PhaseCategoryBudget trong một lần gọi.

**Body:**
```json
{
  "phaseId": 2,
  "categoryId": 1,
  "budgetLimit": 25000000
}
```
> Validation:
> - `budgetLimit` mới của category phải `>= currentSpent` của chính category đó.
> - Tổng budget của phase sau cập nhật (`currentTotal - oldBudget + newBudget`) phải `<= project_phases.budget_limit`.
> - Chỉ update đúng 1 record `phase_category_budgets` theo (`phaseId`, `categoryId`).

**Response:** Giống `GET /team-leader/projects/:id/categories`.

---

### DELETE `/team-leader/projects/:id/categories`
Team Leader xóa **1** PhaseCategoryBudget trong một lần gọi.

**Body:**
```json
{
  "phaseId": 2,
  "categoryId": 1
}
```
> Validation:
> - Chỉ được xóa khi `currentSpent == 0`.
> - Nếu không tồn tại (`phaseId`, `categoryId`) thì trả lỗi not found.

**Response:** Giống `GET /team-leader/projects/:id/categories`.

---

### GET `/team-leader/expense-categories?projectId={projectId}`
Trả về danh sách category khả dụng cho Team Leader khi cấu hình ngân sách category của dự án, bao gồm:
- Category hệ thống (`isSystemDefault = true`)
- Category riêng đã được tạo cho đúng project (`project_id = projectId`, `isSystemDefault = false`)

**Query params:**
- `projectId` (required, Long)

**Response:**
```json
[
  { "id": 1, "name": "Travel & Accommodation", "description": "Công tác phí, di chuyển, khách sạn", "isSystemDefault": true },
  { "id": 2, "name": "Equipment & Software", "description": "Mua sắm thiết bị, bản quyền phần mềm", "isSystemDefault": true },
  { "id": 15, "name": "Integration Testing Vendor", "description": "Chi phí vendor test tích hợp dự án", "isSystemDefault": false }
]
```

### POST `/team-leader/projects/:id/expense-categories`
Team Leader tạo mới category riêng cho project (`isSystemDefault = false`, `project = :id`) và đồng thời tạo luôn `phase_category_budgets` cho 1 phase cụ thể trong cùng transaction.

**Body:**
```json
{
  "name": "Integration Testing Vendor",
  "description": "Chi phí vendor test tích hợp dự án",
  "phaseId": 2,
  "budgetLimit": 10000000
}
```

**Response (201 Created):**
```json
{
  "id": 15,
  "name": "Integration Testing Vendor",
  "description": "Chi phí vendor test tích hợp dự án",
  "isSystemDefault": false,
  "projectId": 1
}
```

---

## 4. MANAGER

> **Vai trò:** Phân bổ vốn cho Dự án, quản lý dự án cấp cao. Manager duyệt **DUY NHẤT** đơn `PROJECT_TOPUP` (Luồng 2).
> Manager **KHÔNG can thiệp** vào chi tiêu cá nhân (ADVANCE/EXPENSE/REIMBURSE).

---

### GET `/manager/approvals`
Danh sách requests xin cấp vốn dự án chờ Manager duyệt (`status = PENDING`, `type = PROJECT_TOPUP`, thuộc department của manager).

**Params:** `?search=string&page=1&limit=20`

**DB mapping:** `requests` WHERE `status = PENDING` AND `type = PROJECT_TOPUP` AND `project.department_id = manager.department_id`. JOIN `users` (requester) + `projects`.

**Response:**
```json
{
  "items": [
    {
      "id": 201,
      "requestCode": "REQ-ENG-0326-001",
      "type": "PROJECT_TOPUP",
      "status": "PENDING",
      "amount": 50000000,
      "description": "Xin cấp thêm vốn cho Phase 2 — thiếu ngân sách Equipment.",
      "requester": {
        "id": 8,
        "fullName": "Le Van Minh",
        "avatar": "https://res.cloudinary.com/.../signed...",
        "employeeCode": "MK008",
        "jobTitle": "Team Leader",
        "email": "le.minh@ifms.vn"
      },
      "project": {
        "id": 1,
        "projectCode": "PRJ-ERP-2026",
        "name": "ERP Integration",
        "availableBudget": 20000000
      },
      "createdAt": "2026-03-05T09:15:00"
    }
  ],
  "total": 2,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `project.availableBudget`: `projects.available_budget` — số dư hiện tại của Project Fund.

---

### GET `/manager/approvals/:id`
Chi tiết một request PROJECT_TOPUP cần Manager duyệt.

**Response:**
```json
{
  "id": 201,
  "requestCode": "REQ-ENG-0326-001",
  "type": "PROJECT_TOPUP",
  "status": "PENDING",
  "amount": 50000000,
  "approvedAmount": null,
  "description": "Xin cấp thêm vốn cho Phase 2 — thiếu ngân sách Equipment.",
  "rejectReason": null,
  "requester": {
    "id": 8,
    "fullName": "Le Van Minh",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "employeeCode": "MK008",
    "jobTitle": "Team Leader",
    "email": "le.minh@ifms.vn",
    "departmentName": "Engineering"
  },
  "project": {
    "id": 1,
    "projectCode": "PRJ-ERP-2026",
    "name": "ERP Integration",
    "availableBudget": 20000000,
    "totalBudget": 150000000
  },
  "department": {
    "id": 1,
    "name": "Engineering",
    "totalAvailableBalance": 100000000
  },
  "timeline": [],
  "createdAt": "2026-03-05T09:15:00",
  "updatedAt": "2026-03-05T09:15:00"
}
```
> `department.totalAvailableBalance`: ngân sách còn lại của phòng ban (Manager cần biết để quyết định).

---

### POST `/manager/approvals/:id/approve`
Manager duyệt `PROJECT_TOPUP`. Wallet transfer thực hiện **đồng bộ ngay lập tức** trong cùng database transaction — không qua scheduler.

Flow: `PENDING → APPROVED_BY_MANAGER → PAID` (tất cả trong 1 request).

> ⚠️ **KHÔNG CÓ MANAGER_LIMIT, KHÔNG ESCALATE.** Manager có toàn quyền duyệt mọi số tiền, miễn là Department Fund còn đủ.

**Body:**
```json
{ "comment": "string (optional)", "approvedAmount": 50000000 }
```
**Response:**
```json
{
  "id": 201,
  "requestCode": "REQ-ENG-0326-001",
  "status": "PAID",
  "approvedAmount": 50000000,
  "comment": "Approved — Project Fund topped up."
}
```
> Backend tạo 2 `request_histories`: `action = APPROVE` (→ `APPROVED_BY_MANAGER`) và `action = PAYOUT` (→ `PAID`).  
> Wallet operation: `walletService.transfer(DEPARTMENT → PROJECT, PROJECT_QUOTA_ALLOCATION)` — `Wallet(DEPARTMENT) -= approvedAmount`, `Wallet(PROJECT) += approvedAmount`.  
> `requests.paid_at` được set ngay khi approve.  
> **Realtime:** Server push `wallet.updated` + `transaction.created` tới Manager qua `GET /users/department/{departmentId}/stream` và tới Team Leader qua `GET /users/project/{projectId}/stream`.

---

### POST `/manager/approvals/:id/reject`
Từ chối PROJECT_TOPUP. Bắt buộc nhập lý do.

**Body:**
```json
{ "reason": "string" }
```
**Response:**
```json
{
  "id": 201,
  "requestCode": "REQ-ENG-0326-001",
  "status": "REJECTED",
  "rejectReason": "Budget allocation for this quarter already maxed"
}
```
> Backend cập nhật `requests.status = REJECTED`, `requests.reject_reason`. Tạo `request_histories`: `action = REJECT`, `status_after_action = REJECTED`.

> **Realtime — Department Wallet:** Manager có thể subscribe kênh SSE `GET /users/department/{departmentId}/stream` để nhận `wallet.updated` và `transaction.created` khi ví phòng ban thay đổi (tiền ra khi approve PROJECT_TOPUP, tiền vào khi CFO approve DEPARTMENT_TOPUP).

---

### GET `/manager/department/members`
Danh sách nhân viên trong department của manager.

**Params:** `?search=string&page=1&limit=20`

**DB mapping:** `users` WHERE `department_id = manager.department_id` JOIN `user_profiles` JOIN `file_storages` (avatar) + aggregate từ `wallets` (debt_balance) + aggregate `requests` (pending count).

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "fullName": "Nguyen Van An",
      "email": "van.an@ifms.vn",
      "employeeCode": "MK001",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "jobTitle": "Backend Developer",
      "status": "ACTIVE",
      "pendingRequestsCount": 1,
      "debtBalance": 8500000
    }
  ],
  "total": 12,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `id`: `users.id` (Long).  
> `debtBalance`: `wallets.debt_balance` (join qua `user_id`).  
> `pendingRequestsCount`: COUNT `requests` WHERE `requester_id = user.id` AND `status IN (PENDING, APPROVED_BY_TEAM_LEADER)`.

---

### GET `/manager/department/members/:id`
Chi tiết một nhân viên trong department.

**Response:**
```json
{
  "id": 1,
  "fullName": "Nguyen Van An",
  "email": "van.an@ifms.vn",
  "employeeCode": "MK001",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "jobTitle": "Backend Developer",
  "phoneNumber": "+84 901 234 567",
  "status": "ACTIVE",
  "debtBalance": 8500000,
  "pendingRequestsCount": 1,
  "assignedProjects": [
    {
      "projectId": 1,
      "projectCode": "PRJ-ERP-2026",
      "projectName": "ERP Integration",
      "projectRole": "MEMBER",
      "position": "Backend Developer"
    }
  ]
}
```
> `assignedProjects`: join `project_members` → `projects`. `projectRole`: `project_members.project_role` (Enum: LEADER/MEMBER). `position`: `project_members.position` (free text).  

---

### GET `/manager/projects`
Danh sách projects thuộc department của manager.

**Params:** `?status=PLANNING|ACTIVE|PAUSED|CLOSED&search=string&page=1&limit=20`

**DB mapping:** `projects` WHERE `department_id = manager.department_id` + COUNT `project_members`.

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "projectCode": "PRJ-ERP-2026",
      "name": "ERP Integration",
      "status": "ACTIVE",
      "totalBudget": 150000000,
      "availableBudget": 95500000,
      "totalSpent": 113600000,
      "memberCount": 5,
      "currentPhaseId": 2,
      "currentPhaseName": "Phase 2: Development",
      "createdAt": "2026-01-05T09:00:00"
    }
  ],
  "total": 4,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `id`: `projects.id` (Long).  
> `projectCode`: `projects.project_code`.  
> `status`: `ProjectStatus` — `PLANNING | ACTIVE | PAUSED | CLOSED`.  
> `totalBudget` / `totalSpent`: `projects.total_budget` / `projects.total_spent`.  
> `availableBudget`: `projects.available_budget` — ngân sách khả dụng Project Fund.  
> `memberCount`: COUNT `project_members` WHERE `project_id = project.id`.  
> `currentPhaseId` / `currentPhaseName`: join `project_phases` qua `projects.current_phase_id`.

---

### GET `/manager/projects/:id`
Chi tiết project với phases và members.

**DB mapping:** `projects` JOIN `project_phases` + `project_members` → `users` → `user_profiles`.

**Response:**
```json
{
  "id": 1,
  "projectCode": "PRJ-ERP-2026",
  "name": "ERP Integration",
  "description": "Full ERP integration with microservices and API layer.",
  "status": "ACTIVE",
  "totalBudget": 150000000,
  "availableBudget": 95500000,
  "totalSpent": 113600000,
  "departmentId": 1,
  "managerId": 5,
  "currentPhaseId": 2,
  "phases": [
    {
      "id": 2,
      "phaseCode": "PH-DEV-02",
      "name": "Phase 2: Payment Integration",
      "budgetLimit": 60000000,
      "currentSpent": 54500000,
      "status": "ACTIVE",
      "startDate": "2026-01-01",
      "endDate": "2026-03-31"
    }
  ],
  "members": [
    {
      "userId": 1,
      "fullName": "Nguyen Van An",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "employeeCode": "MK001",
      "projectRole": "MEMBER",
      "position": "Backend Developer",
      "joinedAt": "2026-01-05T09:00:00"
    },
    {
      "userId": 8,
      "fullName": "Le Van Minh",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "employeeCode": "MK008",
      "projectRole": "LEADER",
      "position": "Team Leader",
      "joinedAt": "2026-01-05T09:00:00"
    }
  ],
  "createdAt": "2026-01-05T09:00:00",
  "updatedAt": "2026-02-20T14:00:00"
}
```
> `phases[]`: từ `project_phases` WHERE `project_id = project.id`.  
> `members[]`: join `project_members` → `users` → `user_profiles` → `file_storages`.  
> `projectRole`: `project_members.project_role` — Enum: `LEADER | MEMBER`.  
> `position`: `project_members.position` — free text hiển thị (VD: "Backend Dev", "Tester").  
> `joinedAt`: `project_members.joined_at`.

---

### POST `/manager/projects`
Tạo project mới trong department của manager. Backend auto-generate `projectCode`. Tự động set `department_id` = department của manager, `manager_id` = manager hiện tại. `available_budget` = 0 (phải xin cấp vốn qua PROJECT_TOPUP).

> Manager chỉ cần điền thông tin cơ bản và **chỉ định Team Leader** cho dự án. Việc thêm Phase, Member, Category Budget sẽ do Team Leader thực hiện sau.

**Body:**
```json
{
  "name": "New Project Name",
  "description": "Project description",
  "totalBudget": 150000000,
  "teamLeaderId": 8
}
```
> `name`: Tên dự án (required).  
> `description`: Mô tả dự án (optional).  
> `totalBudget`: Ngân sách kế hoạch tổng (required, chỉ mang tính tham chiếu — tiền thực tế phải qua PROJECT_TOPUP).  
> `teamLeaderId`: `users.id` của người được chỉ định làm Team Leader (required). Phải là user thuộc cùng department, role = `TEAM_LEADER` hoặc `EMPLOYEE`. Backend auto-tạo record `project_members` với `project_role = LEADER`.

**Validation:**
- `teamLeaderId` phải thuộc `department_id` của Manager.
- `teamLeaderId` phải là user `status = ACTIVE`.
- `totalBudget` > 0.

**Response:**
```json
{
  "id": 5,
  "projectCode": "PRJ-NEW-2026",
  "name": "New Project Name",
  "description": "Project description",
  "status": "PLANNING",
  "totalBudget": 150000000,
  "availableBudget": 0,
  "totalSpent": 0,
  "departmentId": 1,
  "managerId": 5,
  "currentPhaseId": null,
  "phases": [],
  "members": [
    {
      "userId": 8,
      "fullName": "Le Van Minh",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "employeeCode": "MK008",
      "projectRole": "LEADER",
      "position": "Team Leader",
      "joinedAt": "2026-02-24T10:00:00"
    }
  ],
  "createdAt": "2026-02-24T10:00:00",
  "updatedAt": "2026-02-24T10:00:00"
}
```
> Backend tự động: tạo `project_members` record (user = teamLeaderId, project_role = LEADER, position = "Team Leader").  
> `phases` trả mảng rỗng — Team Leader sẽ thêm Phase sau.  
> `currentPhaseId` = null — chưa có Phase nào.

---

### PUT `/manager/projects/:id`
Cập nhật thông tin project cơ bản. Chỉ cập nhật khi project thuộc department của manager.

**Body:**
```json
{
  "name": "string (optional)",
  "description": "string (optional)",
  "totalBudget": 150000000,
  "status": "ACTIVE",
  "teamLeaderId": 9
}
```
> `status`: optional — cho phép Manager chuyển `ACTIVE → PAUSED`, `PAUSED → ACTIVE`, `ACTIVE → CLOSED`. Khi `PAUSED/CLOSED` → chặn tạo request mới.  
> `teamLeaderId`: optional — đổi Team Leader. Backend: update `project_members` (set LEADER cũ → MEMBER, set user mới → LEADER). User mới phải thuộc cùng department.  
> `totalBudget`: optional — chỉ cho phép tăng, không giảm dưới `totalSpent`.

**Response:** Giống `GET /manager/projects/:id`.

---

### GET `/manager/department/team-leaders`
Danh sách users có role `TEAM_LEADER` trong department của manager. Dùng cho dropdown khi tạo project.

**DB mapping:** `users` WHERE `department_id = manager.department_id` AND `role.name = TEAM_LEADER` AND `status = ACTIVE`.

**Response:**
```json
[
  {
    "id": 8,
    "fullName": "Le Van Minh",
    "employeeCode": "MK008",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "email": "le.minh@ifms.vn",
    "jobTitle": "Team Leader"
  },
  {
    "id": 9,
    "fullName": "Tran Hoang Nam",
    "employeeCode": "MK009",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "email": "hoang.nam@ifms.vn",
    "jobTitle": "Senior Developer"
  }
]
```

---

## 5. ACCOUNTANT

> **Vai trò theo SoD:** Accountant là tầng **execution** cho Flow 1 (`ADVANCE | EXPENSE | REIMBURSE`).  
> Accountant **không quyết định phê duyệt nghiệp vụ** cho Flow 2/3 (PROJECT_TOPUP/DEPARTMENT_TOPUP) — các flow đó auto-pay sau khi Manager/CFO duyệt.  
> Request chờ Accountant xử lý ở trạng thái `APPROVED_BY_TEAM_LEADER`.

---

### GET `/accountant/disbursements`
Danh sách requests Flow 1 đã qua decision stage của Team Leader, chờ Accountant execute (`status = APPROVED_BY_TEAM_LEADER`).

**Params:** `?type=ADVANCE|EXPENSE|REIMBURSE&search=string&page=0&size=20`

**DB mapping:** `requests` WHERE `status = APPROVED_BY_TEAM_LEADER` AND `type IN (ADVANCE, EXPENSE, REIMBURSE)` JOIN `users` + `user_profiles` + `projects` + `project_phases` + `expense_categories` + `request_attachments` → `file_storages` + `request_histories`.

**Response:**
```json
{
  "items": [
    {
      "id": 101,
      "requestCode": "REQ-IT-2602-001",
      "type": "ADVANCE",
      "status": "APPROVED_BY_TEAM_LEADER",
      "amount": 8500000,
      "approvedAmount": 8500000,
      "description": "Q1 advance for dev tools and API licenses.",
      "requester": {
        "id": 1,
        "fullName": "Nguyen Van An",
        "avatar": "https://res.cloudinary.com/.../signed...",
        "employeeCode": "MK001",
        "jobTitle": "Backend Developer",
        "departmentName": "Engineering",
        "bankName": "Techcombank",
        "bankAccountNum": "19036277381012",
        "bankAccountOwner": "NGUYEN VAN AN"
      },
      "project": {
        "id": 1,
        "projectCode": "PRJ-ERP-2026",
        "name": "ERP Integration"
      },
      "phase": {
        "id": 2,
        "phaseCode": "PH-DEV-02",
        "name": "Phase 2 – Development Sprint"
      },
      "categoryId": 1,
      "categoryCode": "CAT-EQP-001",
      "categoryName": "Equipment & Software",
      "attachments": [
        {
          "fileName": "jetbrains_invoice_Q1.jpg",
          "cloudinaryPublicId": "requests/jetbrains_invoice_Q1",
          "url": "https://res.cloudinary.com/.../signed...",
          "fileType": "image/jpeg",
          "size": 245000
        }
      ],
      "createdAt": "2026-02-18T09:00:00"
    }
  ],
  "total": 8,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```
> `requester.bankName` / `bankAccountNum` / `bankAccountOwner`: từ `user_profiles` — đầy đủ (unmasked) cho Accountant giải ngân.

---

### GET `/accountant/disbursements/:id`
Chi tiết một disbursement (request đã được Team Leader duyệt). Response kèm đầy đủ bank info và approval timeline.

**Response:**
```json
{
  "id": 101,
  "requestCode": "REQ-IT-2602-001",
  "type": "ADVANCE",
  "status": "APPROVED_BY_TEAM_LEADER",
  "amount": 8500000,
  "approvedAmount": 8500000,
  "description": "Q1 advance for dev tools and API licenses.",
  "rejectReason": null,
  "requester": {
    "id": 1,
    "fullName": "Nguyen Van An",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "employeeCode": "MK001",
    "jobTitle": "Backend Developer",
    "departmentName": "Engineering",
    "bankName": "Techcombank",
    "bankAccountNum": "19036277381012",
    "bankAccountOwner": "NGUYEN VAN AN"
  },
  "project": {
    "id": 1,
    "projectCode": "PRJ-ERP-2026",
    "name": "ERP Integration"
  },
  "phase": {
    "id": 2,
    "phaseCode": "PH-DEV-02",
    "name": "Phase 2 – Development Sprint",
    "budgetLimit": 80000000,
    "currentSpent": 62500000
  },
  "categoryId": 1,
  "categoryCode": "CAT-EQP-001",
  "categoryName": "Equipment & Software",
  "attachments": [
    {
      "fileId": 10,
      "fileName": "jetbrains_invoice_Q1.jpg",
      "cloudinaryPublicId": "requests/jetbrains_invoice_Q1",
      "url": "https://res.cloudinary.com/.../signed...",
      "fileType": "image/jpeg",
      "size": 245000
    }
  ],
  "timeline": [
    {
      "id": 1,
      "action": "APPROVE",
      "statusAfterAction": "APPROVED_BY_TEAM_LEADER",
      "actorId": 8,
      "actorName": "Le Van Minh",
      "comment": "Chứng từ hợp lệ, chuyển kế toán giải ngân.",
      "createdAt": "2026-02-19T10:30:00"
    }
  ],
  "createdAt": "2026-02-18T09:00:00",
  "updatedAt": "2026-02-19T10:30:00"
}
```

---

### POST `/accountant/disbursements/:id/disburse`
Xác nhận giải ngân cho một request Flow 1 (PAYOUT). Yêu cầu PIN. Backend execute theo kiến trúc ví:
- `walletService.settleAndTransfer(PROJECT -> USER, REQUEST_PAYMENT)`
- Tạo `Transaction` + 2 `LedgerEntry` (DEBIT project wallet, CREDIT user wallet)
- Cập nhật `requests.status = PAID` + `paidAt`
- Cập nhật `phase_category_budgets.current_spent += approvedAmount`

**Body:**
```json
{
  "pin": "string",
  "note": "string (optional)"
}
```
**Response:**
```json
{
  "id": 101,
  "requestCode": "REQ-IT-2602-001",
  "status": "PAID",
  "transactionCode": "TXN-8829145A",
  "amount": 8500000,
  "disbursedAt": "2026-02-22T10:00:00"
}
```
> `pin`: PIN 5 chữ số của Accountant — xác thực qua `user_security_settings`.  
> `note`: ghi chú giải ngân → lưu vào `transactions.description`.  
> `transactionCode`: mã giao dịch nội bộ auto-generated.  
> Backend tạo `request_histories`: `action = PAYOUT`, `status_after_action = PAID`.

---

### POST `/accountant/disbursements/:id/reject`
Từ chối giải ngân (revert request về `REJECTED`). Dùng khi phát hiện sai sót chứng từ phút chót (ngay cả khi Team Leader đã approve nghiệp vụ).

**Body:**
```json
{ "reason": "string" }
```
**Response:**
```json
{
  "id": 101,
  "requestCode": "REQ-IT-2602-001",
  "status": "REJECTED",
  "rejectReason": "Invalid bank account information"
}
```
> Backend cập nhật `requests.status = REJECTED`, `requests.reject_reason`. Tạo `request_histories`: `action = REJECT`.

> Lưu ý theo SoD: đây là reject ở execution checkpoint của Accountant (Flow 1), khác với reject ở decision stage của Team Leader.

---

### GET `/accountant/payroll`
Danh sách tất cả các kỳ lương.

**Params:** `?year=2026&status=DRAFT|PROCESSING|COMPLETED&page=1&limit=12`

**DB mapping:** `payroll_periods` + aggregate COUNT/SUM từ `payslips`.

**Response:**
```json
{
  "items": [
    {
      "id": 3,
      "periodCode": "PR-2026-02",
      "name": "Lương T02/2026",
      "month": 2,
      "year": 2026,
      "startDate": "2026-02-01",
      "endDate": "2026-02-28",
      "status": "DRAFT",
      "employeeCount": 12,
      "totalNetPayroll": 285600000,
      "createdAt": "2026-02-22T10:00:00",
      "updatedAt": "2026-02-22T10:00:00"
    }
  ],
  "total": 14,
  "page": 1,
  "limit": 12,
  "totalPages": 2
}
```
> `id`: `payroll_periods.id` (Long).  
> `periodCode`: `payroll_periods.period_code` — format `PR-{YEAR}-{MM}`.  
> `status`: `PayrollStatus` — `DRAFT | PROCESSING | COMPLETED`.  
> `employeeCount`: COUNT `payslips` WHERE `period_id`.  
> `totalNetPayroll`: SUM `payslips.final_net_salary` WHERE `period_id`.

---

### GET `/accountant/payroll/:periodId`
Chi tiết bảng lương của một kỳ cụ thể, bao gồm toàn bộ entries (payslips).

**DB mapping:** `payroll_periods` + `payslips` JOIN `users` → `user_profiles` → `file_storages` (avatar).

**Response:**
```json
{
  "id": 3,
  "periodCode": "PR-2026-02",
  "name": "Lương T02/2026",
  "month": 2,
  "year": 2026,
  "startDate": "2026-02-01",
  "endDate": "2026-02-28",
  "status": "DRAFT",
  "employeeCount": 12,
  "totalNetPayroll": 285600000,
  "entries": [
    {
      "id": 42,
      "payslipCode": "PSL-MK001-0226",
      "userId": 1,
      "fullName": "Nguyen Van An",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "employeeCode": "MK001",
      "jobTitle": "Backend Developer",
      "baseSalary": 28000000,
      "bonus": 2000000,
      "allowance": 0,
      "deduction": 2800000,
      "advanceDeduct": 8500000,
      "finalNetSalary": 18700000,
      "status": "DRAFT"
    }
  ],
  "createdAt": "2026-02-22T10:00:00",
  "updatedAt": "2026-02-22T10:00:00"
}
```
> `entries[]`: từ `payslips` WHERE `period_id`. Mỗi entry = 1 payslip.  
> `id`: `payslips.id` (Long). `payslipCode`: `payslips.payslip_code`.  
> `baseSalary`, `bonus`, `allowance`, `deduction`, `advanceDeduct`, `finalNetSalary`: map trực tiếp từ `payslips`.  
> `finalNetSalary = baseSalary + bonus + allowance - deduction - advanceDeduct`.  
> `status`: `PayslipStatus` — `DRAFT | PAID`.

---

### POST `/accountant/payroll`
Tạo kỳ lương mới. Backend auto-generate `periodCode`.

**Body:**
```json
{
  "name": "Lương T03/2026",
  "month": 3,
  "year": 2026,
  "startDate": "2026-03-01",
  "endDate": "2026-03-31"
}
```
**Response:**
```json
{
  "id": 4,
  "periodCode": "PR-2026-03",
  "name": "Lương T03/2026",
  "month": 3,
  "year": 2026,
  "startDate": "2026-03-01",
  "endDate": "2026-03-31",
  "status": "DRAFT",
  "employeeCount": 0,
  "totalNetPayroll": 0,
  "entries": [],
  "createdAt": "2026-02-24T09:00:00",
  "updatedAt": "2026-02-24T09:00:00"
}
```
> Trả lỗi `409 Conflict` nếu đã tồn tại period cùng `month` + `year`.

---

### GET `/accountant/payroll/template`
Tải file Excel mẫu để nhập bảng lương. File có sẵn header và format chuẩn.

**Response:** File Excel (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) — download trực tiếp.  
> `Content-Disposition: attachment; filename="payroll_template.xlsx"`  
> Các cột: `employeeCode`, `employeeName`, `baseSalary`, `bonus`, `allowance`, `deduction`.  
> **Lưu ý:** `advanceDeduct` KHÔNG có trong template — field này được tính tự động bởi `POST auto-netting`.

---

### POST `/accountant/payroll/:periodId/import`
Import file Excel bảng lương vào kỳ lương đã tạo. Backend parse, validate từng dòng, map `employeeCode` với `user_profiles.employee_code`, tạo `payslips` records.

**Content-Type:** `multipart/form-data`

| Field  | Type | Required | Mô tả                                  |
|--------|------|----------|----------------------------------------|
| `file` | File | ✔        | File `.xlsx` hoặc `.xls`, tối đa 10 MB |

**Response:**
```json
{
  "periodId": 4,
  "periodCode": "PR-2026-03",
  "status": "DRAFT",
  "totalRows": 15,
  "successCount": 13,
  "errorCount": 2,
  "entries": [
    {
      "id": 50,
      "payslipCode": "PSL-MK001-0326",
      "userId": 1,
      "fullName": "Nguyen Van An",
      "employeeCode": "MK001",
      "baseSalary": 28000000,
      "bonus": 2000000,
      "allowance": 0,
      "deduction": 2800000,
      "advanceDeduct": 0,
      "finalNetSalary": 27200000,
      "status": "DRAFT",
      "importStatus": "ok",
      "importError": null
    },
    {
      "id": null,
      "payslipCode": null,
      "userId": null,
      "fullName": "Nguyen Van X",
      "employeeCode": "MK999",
      "baseSalary": 15000000,
      "bonus": 0,
      "allowance": 0,
      "deduction": 0,
      "advanceDeduct": 0,
      "finalNetSalary": 15000000,
      "status": null,
      "importStatus": "error",
      "importError": "Employee not found in system (row 14)"
    }
  ],
  "errors": [
    { "row": 14, "field": "employeeCode", "message": "Employee not found in system" },
    { "row": 7,  "field": "baseSalary",  "message": "baseSalary must be a positive number" }
  ],
  "totalNetPayroll": 285600000
}
```
> `importStatus`: `ok | error`. Dòng lỗi không được tính vào `totalNetPayroll` và không tạo `payslips` record.  
> Sau import, **bắt buộc** gọi `POST /accountant/payroll/:periodId/auto-netting` rồi mới `POST /accountant/payroll/:periodId/run`.  
> Nếu period đã có payslips, trả `409 Conflict` → gọi `POST /accountant/payroll/:periodId/confirm-overwrite` để xác nhận ghi đè.

---

### POST `/accountant/payroll/:periodId/confirm-overwrite`
Xác nhận ghi đè bảng lương đã có. Gọi sau khi nhận `409` từ import. Backend xoá toàn bộ `payslips` WHERE `period_id = :periodId`.

**Body:** —  
**Response:** `{ "message": "Previous payroll data cleared. Ready for re-import." }`

---

### POST `/accountant/payroll/:periodId/auto-netting`
Tự động tính và điền `advanceDeduct` cho từng payslip dựa trên `wallets.debt_balance` của từng nhân viên. **Bắt buộc gọi trước `/run`.**

**Body:** —

**Response:**
```json
{
  "periodId": 3,
  "periodCode": "PR-2026-02",
  "totalAdvanceDeducted": 35500000,
  "summary": [
    {
      "userId": 1,
      "employeeCode": "MK001",
      "fullName": "Nguyen Van An",
      "outstandingDebt": 8500000,
      "deductedAmount": 8500000,
      "remainingDebt": 0,
      "note": "Full clearance"
    },
    {
      "userId": 5,
      "employeeCode": "MK005",
      "fullName": "Le Thi Hoa",
      "outstandingDebt": 30000000,
      "deductedAmount": 10000000,
      "remainingDebt": 20000000,
      "note": "Partial deduction — capped at 50% net salary"
    }
  ]
}
```
> Quy tắc: `deductedAmount = min(outstandingDebt, 50% * (baseSalary + bonus + allowance - deduction))` — đảm bảo nhân viên nhận ít nhất 50% lương.  
> Sau auto-netting, `payslips.advance_deduct` và `payslips.final_net_salary` được cập nhật tự động.  
> Có thể gọi lại (re-netting) bất kỳ lúc nào khi `status = DRAFT`.

---

### POST `/accountant/payroll/:periodId/run`
Chạy tính lương chính thức. Sinh `transactions` (`PAYSLIP_PAYMENT`) cho từng payslip hợp lệ, chuyển `final_net_salary` vào ví user và ghi nhận bút toán nội bộ theo mô hình wallet.

Theo kiến trúc hiện tại, nguồn chi trả là `Wallet(COMPANY_FUND, ownerId=1)` (không dùng `system_funds`).

> **Bắt buộc:** auto-netting phải được gọi trước. Nếu chưa → `422 Unprocessable Entity`.

**Body:** —  
**Response:**
```json
{
  "periodId": 3,
  "periodCode": "PR-2026-02",
  "status": "PROCESSING",
  "payslipsGenerated": 12,
  "totalNetPayroll": 285600000
}
```
> Sau khi xử lý xong: `payroll_periods.status = COMPLETED`, `payslips.status = PAID`.

---

### PUT `/accountant/payroll/:periodId/entries/:payslipId`
Chỉnh sửa một dòng lương (payslip) trước khi run. Chỉ cho phép khi `payroll_periods.status = DRAFT`.

**Body:**
```json
{
  "baseSalary": 28000000,
  "bonus": 3000000,
  "allowance": 500000,
  "deduction": 2800000,
  "advanceDeduct": 8500000
}
```
> Tất cả fields optional — chỉ cập nhật field được gửi. Backend auto-recalculate `finalNetSalary`.

**Response:**
```json
{
  "id": 42,
  "payslipCode": "PSL-MK001-0226",
  "userId": 1,
  "fullName": "Nguyen Van An",
  "employeeCode": "MK001",
  "baseSalary": 28000000,
  "bonus": 3000000,
  "allowance": 500000,
  "deduction": 2800000,
  "advanceDeduct": 8500000,
  "finalNetSalary": 20200000,
  "status": "DRAFT"
}
```

---

### GET `/accountant/ledger`
Sổ cái giao dịch tài chính hệ thống (trọng tâm các giao dịch tài chính nội bộ + boundary).

**Params:** `?type=DEPOSIT|WITHDRAW|REQUEST_PAYMENT|PAYSLIP_PAYMENT|SYSTEM_ADJUSTMENT|DEPT_QUOTA_ALLOCATION|PROJECT_QUOTA_ALLOCATION&status=SUCCESS|PENDING|FAILED&referenceType=REQUEST|PAYSLIP|PROJECT|DEPARTMENT|SYSTEM&from=2026-01-01&to=2026-02-28&page=1&limit=20`

**DB mapping:** `transactions` + `ledger_entries` + `wallets` + `users` (actor) theo scope truy vấn.

**Response:**
```json
{
  "items": [
    {
      "id": 501,
      "transactionCode": "TXN-8829145A",
      "type": "REQUEST_PAYMENT",
      "status": "SUCCESS",
      "amount": -8500000,
      "balanceAfter": 1250000000,
      "referenceType": "REQUEST",
      "referenceId": 101,
      "description": "Advance — dev tools Q1",
      "actorId": 20,
      "actorName": "Nguyen Thi Thu Ha",
      "createdAt": "2026-02-19T10:30:00"
    }
  ],
  "total": 20,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `id`: `transactions.id` (Long).  
> `transactionCode`: `transactions.transaction_code`.  
> `type`: `TransactionType` — `DEPOSIT | WITHDRAW | REQUEST_PAYMENT | PAYSLIP_PAYMENT | SYSTEM_ADJUSTMENT | DEPT_QUOTA_ALLOCATION | PROJECT_QUOTA_ALLOCATION`.  
> `status`: `TransactionStatus` — `SUCCESS | PENDING | FAILED`.  
> `referenceType`: `ReferenceType` — `REQUEST | PAYSLIP | PROJECT | DEPARTMENT | SYSTEM`. Nullable.  
> `referenceId`: `transactions.reference_id` (Long). Nullable.  
> `actorId` / `actorName`: join `users` qua `transactions.actor_id`.

---

### GET `/accountant/ledger/summary`
Tổng hợp số dư và dòng tiền.

**Params:** `?from=2026-02-01&to=2026-02-28`

**DB mapping:** `wallets` (đặc biệt `COMPANY_FUND`, `FLOAT_MAIN`) + aggregate SUM từ `transactions`.

**Response:**
```json
{
  "currentBalance": 1250000000,
  "totalInflow": 3500000000,
  "totalOutflow": 2250000000,
  "transactionCount": 156
}
```
> `currentBalance`: `Wallet(COMPANY_FUND).balance`.  
> `totalInflow` / `totalOutflow`: SUM `transactions.amount` WHERE `amount > 0` / `amount < 0` trong khoảng thời gian.

> Có thể bổ sung chỉ số toàn vẹn hệ thống từ endpoint reconciliation: `systemDiscrepancy = FLOAT_MAIN - SUM(all wallets except FLOAT_MAIN)`.

---

### GET `/accountant/ledger/:transactionId`
Chi tiết một giao dịch trong sổ cái.

**DB mapping:** `transactions` JOIN `users` (actor) + `wallets` → `users` (wallet owner).

**Response:**
```json
{
  "id": 501,
  "transactionCode": "TXN-8829145A",
  "paymentRef": null,
  "gatewayProvider": "INTERNAL_WALLET",
  "type": "REQUEST_PAYMENT",
  "status": "SUCCESS",
  "amount": -8500000,
  "balanceAfter": 1250000000,
  "referenceType": "REQUEST",
  "referenceId": 101,
  "relatedTransactionId": 502,
  "walletId": 1,
  "walletOwnerName": "System Fund",
  "actorId": 20,
  "actorName": "Nguyen Thi Thu Ha",
  "description": "Advance — dev tools Q1",
  "ledgerEntries": [
    {
      "id": 9001,
      "transactionCode": "TXN-8829145A",
      "direction": "DEBIT",
      "amount": 8500000,
      "balanceAfter": 62500000,
      "createdAt": "2026-02-19T10:30:00"
    },
    {
      "id": 9002,
      "transactionCode": "TXN-8829145A",
      "direction": "CREDIT",
      "amount": 8500000,
      "balanceAfter": 15250000,
      "createdAt": "2026-02-19T10:30:00"
    }
  ],
  "createdAt": "2026-02-19T10:30:00",
  "updatedAt": "2026-02-19T10:30:00"
}
```
> `paymentRef`: `transactions.payment_ref` — mã tham chiếu cổng thanh toán. Nullable.  
> `gatewayProvider`: `PaymentProvider` — `PAYOS | MOMO | VNPAY | ZALOPAY | INTERNAL_WALLET`.  
> `relatedTransactionId`: `transactions.related_transaction_id` — ID giao dịch đối ứng (bút toán kép). Nullable.
> `ledgerEntries`: danh sách bút toán map theo `LedgerEntryResponse` (`id`, `transactionCode`, `direction`, `amount`, `balanceAfter`, `createdAt`) với điều kiện `ledger_entries.transaction_id = :transactionId`.
> Giao dịch boundary (ví dụ `SYSTEM_TOPUP`) có thể chỉ có 1 `LedgerEntry`; giao dịch nội bộ thường có 2 entries (DEBIT/CREDIT).

---


### GET `/accountant/payslips/:payslipId`
Chi tiết một payslip cụ thể. Dùng khi Accountant cần tra cứu payslip từ ledger → `referenceId`.

**DB mapping:** `payslips` JOIN `payroll_periods` + `users` → `user_profiles` + `departments`.

**Response:**
```json
{
  "id": 42,
  "payslipCode": "PSL-MK001-0226",
  "periodId": 3,
  "periodCode": "PR-2026-02",
  "periodName": "Lương T02/2026",
  "month": 2,
  "year": 2026,
  "status": "PAID",
  "baseSalary": 28000000,
  "bonus": 2000000,
  "allowance": 0,
  "totalEarnings": 30000000,
  "deduction": 2800000,
  "advanceDeduct": 8500000,
  "totalDeduction": 11300000,
  "finalNetSalary": 18700000,
  "employee": {
    "id": 1,
    "fullName": "Nguyen Van An",
    "employeeCode": "MK001",
    "departmentName": "Engineering",
    "jobTitle": "Backend Developer",
    "bankName": "Techcombank",
    "bankAccountNum": "****1012"
  },
  "createdAt": "2026-02-22T10:00:00",
  "updatedAt": "2026-02-25T10:00:00"
}
```
> `totalEarnings = baseSalary + bonus + allowance` (computed).  
> `totalDeduction = deduction + advanceDeduct` (computed).  
> `employee.bankAccountNum`: masked — chỉ hiển thị 4 số cuối.

---

## 6. CFO

> **Vai trò theo Financial Architecture:** CFO phụ trách **decision stage** của Flow 3 (`DEPARTMENT_TOPUP`).
> Sau khi CFO duyệt, scheduler auto-pay để cấp quota từ `COMPANY_FUND` sang `DEPARTMENT`.

---

### GET `/cfo/approvals`
Danh sách requests xin cấp vốn phòng ban chờ CFO duyệt (`status = PENDING`, `type = DEPARTMENT_TOPUP`).

**Params:** `?search=string&page=0&size=20`

**DB mapping:** `requests` WHERE `status = PENDING` AND `type = DEPARTMENT_TOPUP`. JOIN `users` (requester — Manager) + `departments`.

**Response:**
```json
{
  "items": [
    {
      "id": 301,
      "requestCode": "REQ-SYS-0326-001",
      "type": "DEPARTMENT_TOPUP",
      "status": "PENDING",
      "amount": 200000000,
      "description": "Xin cấp vốn Q1/2026 cho phòng Engineering.",
      "requester": {
        "id": 5,
        "fullName": "Tran Thi Bich Ngoc",
        "avatar": "https://res.cloudinary.com/.../signed...",
        "employeeCode": "MK005",
        "jobTitle": "Engineering Manager",
        "email": "ngoc@ifms.vn"
      },
      "department": {
        "id": 1,
        "name": "Engineering",
        "code": "ENG",
        "totalAvailableBalance": 50000000
      },
      "createdAt": "2026-03-01T09:00:00"
    }
  ],
  "total": 3,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```
> `department.totalAvailableBalance`: ngân sách phòng ban hiện tại → CFO quyết định mức cấp quota.

---

### GET `/cfo/approvals/:id`
Chi tiết một request `DEPARTMENT_TOPUP` cần CFO duyệt.

**Response:**
```json
{
  "id": 301,
  "requestCode": "REQ-SYS-0326-001",
  "type": "DEPARTMENT_TOPUP",
  "status": "PENDING",
  "amount": 200000000,
  "approvedAmount": null,
  "description": "Xin cấp vốn Q1/2026 cho phòng Engineering.",
  "rejectReason": null,
  "requester": {
    "id": 5,
    "fullName": "Tran Thi Bich Ngoc",
    "avatar": "https://res.cloudinary.com/.../signed...",
    "employeeCode": "MK005",
    "jobTitle": "Engineering Manager",
    "email": "ngoc@ifms.vn",
    "departmentName": "Engineering"
  },
  "department": {
    "id": 1,
    "name": "Engineering",
    "code": "ENG",
    "totalProjectQuota": 500000000,
    "totalAvailableBalance": 50000000
  },
  "companyFund": {
    "balance": 5000000000
  },
  "timeline": [],
  "createdAt": "2026-03-01T09:00:00",
  "updatedAt": "2026-03-01T09:00:00"
}
```
> `companyFund.balance`: `Wallet(COMPANY_FUND).balance` — số dư nguồn cấp quota.

---

### POST `/cfo/approvals/:id/approve`
CFO duyệt `DEPARTMENT_TOPUP`. Status chuyển sang `APPROVED_BY_CFO`; scheduler **auto-pay sau ~1 phút** chuyển sang `PAID`.

Khi auto-pay: `walletService.transfer(COMPANY_FUND → DEPARTMENT, DEPT_QUOTA_ALLOCATION)` — `Wallet(COMPANY_FUND) -= approvedAmount`, `Wallet(DEPARTMENT) += approvedAmount`.

**Body:**
```json
{ "comment": "string (optional)", "approvedAmount": 200000000 }
```
**Response:**
```json
{
  "id": 301,
  "requestCode": "REQ-SYS-0326-001",
  "status": "APPROVED_BY_CFO",
  "approvedAmount": 200000000,
  "comment": "Approved by CFO — waiting auto allocation."
}
```
> Backend tạo `request_histories`: `action = APPROVE`, `status_after_action = APPROVED_BY_CFO`.  
> Scheduler auto-transition: `APPROVED_BY_CFO -> PAID` (transaction type `DEPT_QUOTA_ALLOCATION`).

---

### POST `/cfo/approvals/:id/reject`
CFO từ chối `DEPARTMENT_TOPUP`.

**Body:**
```json
{ "reason": "string" }
```
**Response:**
```json
{
  "id": 301,
  "requestCode": "REQ-SYS-0326-001",
  "status": "REJECTED",
  "rejectReason": "System fund insufficient for this quarter"
}
```

> **Realtime — Company Fund Wallet:** CFO và Accountant có thể subscribe kênh SSE `GET /users/company-fund/stream` để nhận `wallet.updated` và `transaction.created` khi quỹ công ty thay đổi (tiền ra khi CFO approve DEPARTMENT_TOPUP hoặc Accountant chạy payroll, tiền vào khi SYSTEM_TOPUP).

---

## 7. ADMIN

> **Vai trò theo RBAC hiện tại:** Admin tập trung IAM + cấu hình hệ thống.
> Admin không giữ financial approval flow cho `DEPARTMENT_TOPUP`.

---

### GET `/admin/users`
Danh sách tất cả users trong hệ thống.

**Params:** `?role=EMPLOYEE|TEAM_LEADER|MANAGER|ACCOUNTANT|ADMIN&departmentId=1&status=ACTIVE|LOCKED|PENDING&search=nguyen&page=1&limit=20`

**DB mapping:** `users` JOIN `roles` + `departments` + `user_profiles` + LEFT JOIN `wallets`.

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "fullName": "Nguyen Van An",
      "email": "van.an@ifms.vn",
      "employeeCode": "MK001",
      "role": "EMPLOYEE",
      "departmentId": 1,
      "departmentName": "Engineering",
      "jobTitle": "Backend Developer",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "debtBalance": 0,
      "status": "ACTIVE",
      "createdAt": "2024-01-15T00:00:00"
    }
  ],
  "total": 21,
  "page": 1,
  "limit": 20,
  "totalPages": 2
}
```
> `id`: `users.id` (Long).  
> `role`: `roles.name`.  
> `employeeCode`: `user_profiles.employee_code`.  
> `debtBalance`: `wallets.debt_balance`. `0` nếu chưa có ví.

---

### GET `/admin/users/:id`
Chi tiết đầy đủ một user.

**DB mapping:** `users` JOIN `roles` + `departments` + `user_profiles` → `file_storages` + `user_security_settings` + `wallets`.

**Response:**
```json
{
  "id": 1,
  "fullName": "Nguyen Van An",
  "email": "van.an@ifms.vn",
  "employeeCode": "MK001",
  "role": "EMPLOYEE",
  "departmentId": 1,
  "departmentName": "Engineering",
  "jobTitle": "Backend Developer",
  "phoneNumber": "+84 901 234 567",
  "dateOfBirth": "1995-06-20",
  "citizenId": "079012345678",
  "address": "123 Nguyen Trai, Thanh Xuan, Ha Noi",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "status": "ACTIVE",
  "isFirstLogin": false,
  "bankInfo": {
    "bankName": "MB Bank",
    "accountNumber": "0123456789",
    "accountOwner": "NGUYEN VAN AN"
  },
  "wallet": {
    "balance": 10250000,
    "pendingBalance": 0,
    "debtBalance": 8500000
  },
  "securitySettings": {
    "hasPIN": true,
    "pinLockedUntil": null,
    "retryCount": 0
  },
  "createdAt": "2024-01-15T00:00:00",
  "updatedAt": "2026-02-20T14:00:00"
}
```
> `wallet`: từ `wallets` WHERE `user_id = user.id`. Nullable nếu chưa có ví.  
> `securitySettings.hasPIN`: `true` nếu `user_security_settings.transaction_pin IS NOT NULL`.  
> `securitySettings.pinLockedUntil`: `user_security_settings.locked_until`.  
> `securitySettings.retryCount`: `user_security_settings.retry_count`.

---

### POST `/admin/users`
Tạo tài khoản mới. Hệ thống tự generate mật khẩu tạm (BCrypt hash), gửi qua email. Auto-create `user_profiles`, `user_security_settings`, `wallet`.

**Body:**
```json
{
  "fullName": "Nguyen Van X",
  "email": "van.x@ifms.vn",
  "roleId": 1,
  "departmentId": 1
}
```
> `roleId`: `roles.id` (Long).  
> `departmentId`: `departments.id` (Long). Optional — nullable nếu chưa gán phòng ban.

**Response:**
```json
{
  "id": 22,
  "fullName": "Nguyen Van X",
  "email": "van.x@ifms.vn",
  "role": "EMPLOYEE",
  "departmentId": 1,
  "departmentName": "Engineering",
  "status": "ACTIVE",
  "isFirstLogin": true,
  "createdAt": "2026-02-22T10:00:00"
}
```

---

### PUT `/admin/users/:id`
Cập nhật thông tin user (role, department, tên).

**Body:**
```json
{
  "fullName": "string (optional)",
  "roleId": 2,
  "departmentId": 1
}
```
> Tất cả fields optional. Backend tạo `audit_logs` record: `action = USER_UPDATED`.

**Response:**
```json
{
  "id": 1,
  "fullName": "Nguyen Van An",
  "email": "van.an@ifms.vn",
  "employeeCode": "MK001",
  "role": "EMPLOYEE",
  "departmentId": 1,
  "departmentName": "Engineering",
  "jobTitle": "Backend Developer",
  "phoneNumber": "+84 901 234 567",
  "dateOfBirth": "1995-06-20",
  "citizenId": "079012345678",
  "address": "123 Nguyen Trai, Thanh Xuan, Ha Noi",
  "avatar": "https://res.cloudinary.com/.../signed...",
  "status": "ACTIVE",
  "isFirstLogin": false,
  "bankInfo": {
    "bankName": "MB Bank",
    "accountNumber": "0123456789",
    "accountOwner": "NGUYEN VAN AN"
  },
  "wallet": {
    "balance": 10250000,
    "pendingBalance": 0,
    "debtBalance": 8500000
  },
  "securitySettings": {
    "hasPIN": true,
    "pinLockedUntil": null,
    "retryCount": 0
  },
  "createdAt": "2024-01-15T00:00:00",
  "updatedAt": "2026-02-24T14:00:00"
}
```

---

### POST `/admin/users/:id/lock`
Khoá tài khoản (`status = LOCKED`). User không thể đăng nhập.

**Response:**
```json
{ "id": 8, "status": "LOCKED" }
```
> Backend tạo `audit_logs`: `action = USER_LOCKED`.

---

### POST `/admin/users/:id/unlock`
Mở khoá tài khoản (`status = ACTIVE`).

**Response:**
```json
{ "id": 8, "status": "ACTIVE" }
```
> Backend tạo `audit_logs`: `action = USER_UNLOCKED`.

---

### POST `/admin/users/:id/reset-password`
Reset mật khẩu về mật khẩu tạm, gửi qua email. Set `is_first_login = true`.

**Response:** `{ "message": "Temporary password sent to user email" }`

---

### GET `/admin/departments`
Danh sách department.

**Params:** `?search=string&page=1&limit=20`

**DB mapping:** `departments` JOIN `users` (manager) + COUNT `users` WHERE `department_id`.

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "name": "Engineering",
      "code": "ENG",
      "manager": {
        "id": 5,
        "fullName": "Tran Thi Bich Ngoc"
      },
      "employeeCount": 12,
      "totalProjectQuota": 500000000,
      "totalAvailableBalance": 150000000,
      "createdAt": "2024-01-01T00:00:00"
    }
  ],
  "total": 5,
  "page": 1,
  "limit": 20,
  "totalPages": 1
}
```
> `id`: `departments.id` (Long).  
> `code`: `departments.code` — mã phòng ban unique.  
> `manager`: nullable nếu chưa gán.  
> `totalProjectQuota`: `departments.total_project_quota` — tổng ngân sách đã cấp.  
> `totalAvailableBalance`: `departments.total_available_balance` — ngân sách còn lại.  
> `employeeCount`: COUNT `users` WHERE `department_id = dept.id`.

---

### GET `/admin/departments/:id`
Chi tiết department kèm danh sách members.

**Response:**
```json
{
  "id": 1,
  "name": "Engineering",
  "code": "ENG",
  "manager": {
    "id": 5,
    "fullName": "Tran Thi Bich Ngoc"
  },
  "totalProjectQuota": 500000000,
  "totalAvailableBalance": 150000000,
  "members": [
    {
      "id": 1,
      "fullName": "Nguyen Van An",
      "employeeCode": "MK001",
      "email": "van.an@ifms.vn",
      "jobTitle": "Backend Developer",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "status": "ACTIVE"
    }
  ],
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2026-02-20T14:00:00"
}
```
> `members[]`: `users` WHERE `department_id = dept.id` JOIN `user_profiles` + `file_storages`.

---

### POST `/admin/departments`
Tạo department mới. Backend auto-generate `code`.

**Body:**
```json
{
  "name": "Data & Analytics",
  "code": "DNA",
  "managerId": 15,
  "totalProjectQuota": 80000000
}
```
> `managerId`: optional — `users.id` (Long). Nếu không truyền, department chưa có manager.  
> `totalProjectQuota`: optional, default `0`.  
> `code`: optional nếu muốn auto-generate.  
> Trả lỗi `409 Conflict` nếu `name` hoặc `code` đã tồn tại.

**Response:**
```json
{
  "id": 6,
  "name": "Data & Analytics",
  "code": "DNA",
  "manager": {
    "id": 15,
    "fullName": "Pham Van Tuan"
  },
  "totalProjectQuota": 80000000,
  "totalAvailableBalance": 80000000,
  "members": [],
  "createdAt": "2026-02-24T10:00:00",
  "updatedAt": "2026-02-24T10:00:00"
}
```

---

### PUT `/admin/departments/:id`
Cập nhật department.

**Body:**
```json
{
  "name": "string (optional)",
  "managerId": 5,
  "totalProjectQuota": 200000000
}
```
> Backend tạo `audit_logs`: `action = DEPARTMENT_UPDATED` hoặc `QUOTA_ADJUSTED` nếu thay đổi quota.

**Response:**
```json
{
  "id": 1,
  "name": "Engineering",
  "code": "ENG",
  "manager": {
    "id": 5,
    "fullName": "Tran Thi Bich Ngoc"
  },
  "totalProjectQuota": 200000000,
  "totalAvailableBalance": 150000000,
  "members": [
    {
      "id": 1,
      "fullName": "Nguyen Van An",
      "employeeCode": "MK001",
      "email": "van.an@ifms.vn",
      "jobTitle": "Backend Developer",
      "avatar": "https://res.cloudinary.com/.../signed...",
      "status": "ACTIVE"
    }
  ],
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2026-02-24T14:00:00"
}
```

---

### GET `/admin/audit`
Lịch sử audit log toàn hệ thống.

**Params:** `?actorId=1&action=INSERT|UPDATE|DELETE&entityName=User|Department|SystemConfig&from=2026-01-01&to=2026-02-28&page=1&limit=50`

**DB mapping:** `audit_logs` JOIN `users` (actor).

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "actorId": 10,
      "actorName": "Le Van Duc",
      "action": "UPDATE",
      "entityName": "User",
      "entityId": "8",
      "oldValues": "{\"status\":\"ACTIVE\"}",
      "newValues": "{\"status\":\"LOCKED\"}",
      "traceId": "uuid-v4",
      "createdAt": "2026-02-22T10:00:00"
    }
  ],
  "total": 10,
  "page": 1,
  "limit": 50,
  "totalPages": 1
}
```
> `id`: `audit_logs.id` (Long).  
> `actorId` / `actorName`: join `users` qua `audit_logs.actor_id`. Nullable (system-triggered).  
> `action`: `AuditAction` enum — **3 giá trị:** `INSERT | UPDATE | DELETE` (phản ánh DML operation cơ bản).  
> `entityName`: tên entity bị tác động (VD: `User`, `Department`, `SystemConfig`, `PayrollPeriod`).  
> `entityId`: ID dòng dữ liệu bị tác động (String).  
> `oldValues` / `newValues`: JSON string snapshot trạng thái trước/sau. Nullable.  
> `traceId`: UUID để trace một operation qua nhiều bảng.

---

### GET `/admin/settings`
Lấy cấu hình hệ thống. Mỗi config là một record trong `system_configs`.

**DB mapping:** `system_configs` — trả về dạng object aggregated.

**Response:**
```json
{
  "items": [
    { "key": "WITHDRAWAL_LIMIT", "value": "50000000", "description": "Hạn mức rút tiền tối đa mỗi lần (VND)" },
    { "key": "MINIMUM_WITHDRAWAL", "value": "10000", "description": "Số tiền rút tối thiểu (VND)" },
    { "key": "MAX_FILE_SIZE_MB", "value": "5", "description": "Dung lượng file upload tối đa (MB)" },
    { "key": "MAX_FILES_PER_REQUEST", "value": "5", "description": "Số file tối đa mỗi request" },
    { "key": "MINIMUM_REQUEST_AMOUNT", "value": "10000", "description": "Số tiền tối thiểu mỗi request (VND)" },
    { "key": "PIN_MAX_RETRY", "value": "5", "description": "Số lần nhập sai PIN tối đa trước khi khoá" },
    { "key": "PIN_LOCK_DURATION_MINUTES", "value": "30", "description": "Thời gian khoá PIN (phút)" },
    { "key": "REQUIRE_PIN_FOR_WITHDRAWAL", "value": "true", "description": "Yêu cầu PIN khi rút tiền" }
  ]
}
```
> Mỗi item = 1 record `system_configs`. `key` = PK.  
> ⚠️ **Không còn `MANAGER_APPROVAL_LIMIT`** — hệ thống sử dụng Balance Limit (số dư quỹ) làm chốt chặn duy nhất.

---

### PUT `/admin/settings`
Cập nhật cấu hình hệ thống. Gửi danh sách key-value cần cập nhật.

**Body:**
```json
{
  "configs": [
    { "key": "WITHDRAWAL_LIMIT", "value": "100000000" },
    { "key": "PIN_MAX_RETRY", "value": "3" }
  ]
}
```
> Chỉ cập nhật các key được gửi. Backend tạo `audit_logs`: `action = CONFIG_UPDATED` cho mỗi key thay đổi.

**Response:**
```json
{
  "items": [
    { "key": "WITHDRAWAL_LIMIT", "value": "100000000", "description": "Hạn mức rút tiền tối đa mỗi lần (VND)" },
    { "key": "MINIMUM_WITHDRAWAL", "value": "10000", "description": "Số tiền rút tối thiểu (VND)" },
    { "key": "MAX_FILE_SIZE_MB", "value": "5", "description": "Dung lượng file upload tối đa (MB)" },
    { "key": "MAX_FILES_PER_REQUEST", "value": "5", "description": "Số file tối đa mỗi request" },
    { "key": "MINIMUM_REQUEST_AMOUNT", "value": "10000", "description": "Số tiền tối thiểu mỗi request (VND)" },
    { "key": "PIN_MAX_RETRY", "value": "3", "description": "Số lần nhập sai PIN tối đa trước khi khoá" },
    { "key": "PIN_LOCK_DURATION_MINUTES", "value": "30", "description": "Thời gian khoá PIN (phút)" },
    { "key": "REQUIRE_PIN_FOR_WITHDRAWAL", "value": "true", "description": "Yêu cầu PIN khi rút tiền" }
  ]
}
```

---

## 8. WEBSOCKET – Real-time Channels

> **Transport:** SockJS + STOMP over WebSocket (Spring Boot `spring-boot-starter-websocket`).  
> **Endpoint kết nối:** `wss://api.ifms.vn/ws` — Client gửi `Authorization: Bearer <accessToken>` qua STOMP `CONNECT` header.  
> **Thư viện FE:** `@stomp/stompjs` + `sockjs-client` (hoặc native WebSocket).  
> **Quy ước:** Mỗi user subscribe vào các channel riêng theo `userId`. Backend publish message qua `SimpMessagingTemplate.convertAndSendToUser()`.

---

### Channel 1: `/user/queue/wallet` — Live Wallet Balance

**Mục đích:** Cập nhật số dư ví real-time khi có giao dịch thành công (giải ngân, chi lương, nạp tiền, rút tiền).

**Trigger (Backend publish khi):**
- `POST /accountant/disbursements/:id/disburse` → employee nhận tiền
- `POST /accountant/payroll/:periodId/run` → batch publish cho tất cả employee có payslip
- `POST /wallet/withdraw` → chính user rút tiền (confirm từ gateway callback)
- Webhook deposit (nạp tiền qua VietQR) → confirm từ payment gateway
- `SYSTEM_ADJUSTMENT` transaction được tạo

**Subscribe:** `/user/queue/wallet`  
> STOMP route tự động prefix `/user/{userId}` — mỗi user chỉ nhận message của mình.

**Message payload:**
```json
{
  "type": "WALLET_UPDATED",
  "data": {
    "walletId": 1,
    "balance": 20000000,
    "pendingBalance": 0,
    "debtBalance": 0,
    "version": 12,
    "transaction": {
      "id": 501,
      "transactionCode": "TXN-8829145A",
      "type": "PAYSLIP_PAYMENT",
      "status": "SUCCESS",
      "amount": 15000000,
      "balanceAfter": 20000000,
      "referenceType": "PAYSLIP",
      "referenceId": 42,
      "description": "Lương T02/2026",
      "createdAt": "2026-02-25T10:00:00"
    }
  },
  "timestamp": "2026-02-25T10:00:00"
}
```
> `balance`, `pendingBalance`, `debtBalance`, `version`: snapshot mới nhất từ `wallets` sau giao dịch.  
> `transaction`: thông tin giao dịch vừa SUCCESS — FE dùng để hiển thị toast/animation.  
> `transaction.type`: xác định loại hiệu ứng — `PAYSLIP_PAYMENT` / `REQUEST_PAYMENT` → "Ting ting" + flash xanh lá, `WITHDRAW` → flash cam, `DEPOSIT` → flash xanh dương.

**FE xử lý:**
1. Nhận message → cập nhật wallet state (Redux/Zustand) → re-render số dư.
2. Hiển thị toast notification: _"+ 15.000.000đ — Lương T02/2026"_.
3. Animate số dư (count-up từ giá trị cũ → giá trị mới).
4. Phát âm thanh "Ting ting" (nếu `amount > 0`).
5. Cập nhật `version` để đảm bảo Optimistic Lock consistency.

---

### ~~Channel 2: `/user/queue/requests`~~ — ĐÃ XÓA (v3.3+)

> ⚠ **Deprecated:** Kênh STOMP `REQUEST_STATUS_CHANGED` đã bị loại bỏ kể từ v3.3 khi hệ thống chuyển sang **Server-Sent Events (SSE)**.  
> Backend **không còn emit** event riêng cho request status changes.  
> Frontend tự **refetch** list/detail sau mỗi action approve/reject/disburse.  
>
> Xem §15 (SSE Realtime) để biết các events hiện tại: `wallet.updated`, `transaction.created`, `notification`.
>
> **Status enum đúng hiện tại:**  
> `PENDING` → `APPROVED_BY_TEAM_LEADER` → `PAID` (Flow 1)  
> `PENDING` → `APPROVED_BY_MANAGER` → `PAID` (Flow 2)  
> `PENDING` → `APPROVED_BY_CFO` → `PAID` (Flow 3)
> `previousStatus` / `newStatus`: trạng thái trước/sau — FE dùng để animate badge transition.  
> `actor`: người thực hiện action — hiển thị trong toast _"Le Van Minh đã duyệt đơn REQ-IT-2602-001"_.  
> `rejectReason`: chỉ có khi `newStatus = REJECTED`.  
> `approvedAmount`: chỉ có khi approve (có thể khác `amount` gốc).  
> `comment`: ghi chú từ người duyệt. Nullable.

**FE xử lý:**
1. Nhận message → cập nhật request status trong state/cache.
2. Nếu đang ở trang request detail (`/requests/101`) → animate badge color transition (vàng → xanh / đỏ).
3. Nếu đang ở trang request list → cập nhật row tương ứng + update request summary counters.
4. Hiển thị toast: _"Đơn REQ-IT-2602-001 đã được duyệt bởi Le Van Minh"_.
5. Cập nhật notification badge (unread +1).

---

### Channel 3: `/user/queue/notifications` — Live Notifications

**Mục đích:** Push notification real-time (thay vì polling `GET /notifications`).

**Trigger:** Mỗi khi Backend tạo record mới trong bảng `notifications`.

**Subscribe:** `/user/queue/notifications`

**Message payload:**
```json
{
  "type": "NEW_NOTIFICATION",
  "data": {
    "id": 55,
    "type": "REQUEST_APPROVED",
    "title": "Request Approved",
    "message": "Your request REQ-IT-2602-001 has been approved by manager",
    "isRead": false,
    "refId": 101,
    "refType": "REQUEST",
    "createdAt": "2026-02-25T10:30:00"
  },
  "timestamp": "2026-02-25T10:30:00"
}
```
> Payload giống 1 item trong `GET /notifications` response.  
> FE nhận → prepend vào notification list + tăng `unreadCount` badge.

---

### Backend Implementation Notes

**Spring Boot config:**
```
// WebSocketConfig.java
@EnableWebSocketMessageBroker
- Registry: /ws (SockJS fallback)
- Application prefix: /app
- User destination prefix: /user
- STOMP broker: /queue, /topic
```

**Publish pattern:**
```java
// Khi disbursement thành công:
messagingTemplate.convertAndSendToUser(
    userId.toString(),          // username = userId
    "/queue/wallet",            // destination
    walletUpdateMessage         // payload
);

// Khi request status thay đổi:
messagingTemplate.convertAndSendToUser(
    requesterId.toString(),
    "/queue/requests",
    requestStatusMessage
);

// Batch payroll (50 người):
payslips.forEach(payslip -> {
    messagingTemplate.convertAndSendToUser(
        payslip.getUserId().toString(),
        "/queue/wallet",
        buildWalletMessage(payslip)
    );
});
```

**Authentication:** Intercept STOMP `CONNECT` frame → extract JWT from header → validate → set `Principal.name = userId`. Reject nếu token invalid/expired.

**Reconnection:** FE xử lý auto-reconnect (exponential backoff: 1s → 2s → 4s → 8s → max 30s). Khi reconnect thành công → gọi `GET /wallet` + `GET /notifications` để sync lại state (tránh miss message khi offline).
