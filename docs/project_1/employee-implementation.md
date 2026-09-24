# Employee — Request Module Implementation

> **Role:** `EMPLOYEE`  
> **Scope:** `modules/request`  
> **Cập nhật:** 2026-04-22

---

## 1. Enum & Constants

```
RequestType:
  ADVANCE          — Tạm ứng tiền mặt
  EXPENSE          — Chi tiêu có chứng từ (chưa ứng trước)
  REIMBURSE        — Quyết toán tạm ứng đã được duyệt
  PROJECT_TOPUP    — Xin cấp thêm vốn cho dự án (Flow 2)
  DEPARTMENT_TOPUP — Xin cấp vốn cho phòng ban (Flow 3)

RequestStatus:
  PENDING_APPROVAL   — Chờ Team Leader / Manager / CFO duyệt
  PENDING_ACCOUNTANT — Đã duyệt, chờ Accountant giải ngân
  APPROVED           — Trung gian, auto-transition sang PAID (Flow 2/3)
  PAID               — Đã giải ngân
  REJECTED           — Bị từ chối
  CANCELLED          — Employee tự hủy
```

---

## 2. Request Flows

```
Flow 1 — ADVANCE / EXPENSE / REIMBURSE
  PENDING_APPROVAL
    → (Team Leader approve) → PENDING_ACCOUNTANT
    → (Accountant payout)   → PAID
    → (Team Leader reject)  → REJECTED

Flow 2 — PROJECT_TOPUP
  PENDING_APPROVAL
    → (Manager approve) → APPROVED → PAID  (auto-pay)
    → (Manager reject)  → REJECTED

Flow 3 — DEPARTMENT_TOPUP
  PENDING_APPROVAL
    → (CFO approve) → APPROVED → PAID  (auto-pay)
    → (CFO reject)  → REJECTED

Terminal: REJECTED, CANCELLED (employee tự hủy khi PENDING_APPROVAL)
```

---

## 3. Quy tắc field theo RequestType

| Field | ADVANCE | EXPENSE | REIMBURSE | PROJECT_TOPUP | DEPT_TOPUP |
|---|---|---|---|---|---|
| `projectId` | Bắt buộc | Bắt buộc | Bắt buộc | Bắt buộc | null |
| `phaseId` | Bắt buộc | Bắt buộc | Bắt buộc | null | null |
| `categoryId` | Bắt buộc | Bắt buộc | Bắt buộc | null | null |
| `advanceBalanceId` | null | null | **Bắt buộc** | null | null |
| `attachments` | Optional | **Bắt buộc** | **Bắt buộc** | null | null |

> `advanceBalanceId`: ID của `AdvanceBalance` đang `PENDING_SETTLEMENT` thuộc chính user.

---

## 4. Endpoints

### `GET /requests`
Danh sách requests của employee hiện tại, mới nhất trước.

**Query params:**

| Param | Type | Default | Mô tả |
|---|---|---|---|
| `type` | `RequestType` | — | Filter theo loại |
| `status` | `RequestStatus` | — | Filter theo trạng thái |
| `search` | string | — | Tìm theo `requestCode` hoặc `description` |
| `page` | int | 1 | Trang hiện tại |
| `limit` | int | 20 | Số item mỗi trang |

**Response:** `PageResponse<RequestSummaryResponse>`
```json
{
  "items": [
    {
      "id": 101,
      "requestCode": "REQ-IT-2602-001",
      "type": "ADVANCE",
      "status": "PENDING_APPROVAL",
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
}
```

> `approvedAmount`: null nếu chưa duyệt; Team Leader có thể duyệt số nhỏ hơn `amount`.  
> `rejectReason`: null nếu chưa bị từ chối.  
> `projectId/projectName`: null với DEPARTMENT_TOPUP.  
> `phaseId/phaseName`: null với PROJECT_TOPUP, DEPARTMENT_TOPUP.  
> `categoryId/categoryName`: null với PROJECT_TOPUP, DEPARTMENT_TOPUP.

---

### `GET /requests/summary`
Badge counts theo status — dùng cho dashboard.

**Response:**
```json
{
  "totalPendingApproval": 2,
  "totalPendingAccountant": 1,
  "totalApproved": 12,
  "totalRejected": 2,
  "totalPaid": 8,
  "totalCancelled": 1
}
```

> Computed: `COUNT GROUP BY status WHERE requester_id = currentUser.id`.

---

### `GET /requests/:id`
Chi tiết đầy đủ một request — bao gồm file đính kèm và timeline duyệt.

**Response:** `RequestDetailResponse`
```json
{
  "id": 101,
  "requestCode": "REQ-IT-2602-001",
  "type": "ADVANCE",
  "status": "PENDING_APPROVAL",
  "amount": 5000000,
  "approvedAmount": null,
  "description": "Advance payment for development team travel expenses",
  "rejectReason": null,
  "projectId": 1,
  "projectCode": "PRJ-ERP-2026",
  "projectName": "E-Commerce Platform",
  "phaseId": 2,
  "phaseCode": "PH-PAY-01",
  "phaseName": "Phase 2: Payment Integration",
  "categoryId": 1,
  "categoryName": "Travel & Accommodation",
  "requesterId": 1,
  "requesterName": "Nguyen Van A",
  "attachments": [
    {
      "fileId": 10,
      "fileName": "Travel_Itinerary.pdf",
      "cloudinaryPublicId": "requests/file_adv_001",
      "url": "https://res.cloudinary.com/.../signed...",
      "fileType": "application/pdf",
      "size": 156789
    }
  ],
  "timeline": [
    {
      "id": 1,
      "action": "APPROVE",
      "statusAfterAction": "PENDING_ACCOUNTANT",
      "actorId": 8,
      "actorName": "Le Van Minh",
      "comment": "Approved",
      "createdAt": "2026-02-16T10:30:00"
    }
  ],
  "createdAt": "2026-02-15T09:30:00",
  "updatedAt": "2026-02-15T09:30:00"
}
```

> `attachments[].url`: Signed URL Cloudinary, TTL 15 phút.  
> `timeline[].action`: `APPROVE | REJECT | PAYOUT | CANCEL`.  
> `timeline[].statusAfterAction`: trạng thái request SAU khi action thực hiện.  
> `categoryId/categoryName`: bắt buộc với ADVANCE/EXPENSE/REIMBURSE, null với PROJECT_TOPUP/DEPARTMENT_TOPUP.

---

### `POST /requests`
Tạo request mới. Backend auto-generate `requestCode` theo format `REQ-{DEPT_CODE}-{MMYY}-{SEQ}`.

**Upload file trước khi gửi request (bắt buộc với EXPENSE / REIMBURSE):**
```
1. GET /uploads/signature?folder=REQUEST  →  nhận signature, apiKey, cloudName
2. Client upload file lên Cloudinary trực tiếp
3. Cloudinary trả về cloudinaryPublicId, url, fileType, size
4. Đưa các giá trị đó vào mảng attachments khi gọi POST /requests
```

**Body — ADVANCE**
```json
{
  "type": "ADVANCE",
  "projectId": 1,
  "phaseId": 2,
  "categoryId": 1,
  "amount": 5000000,
  "description": "Advance for development tools Q1",
  "attachments": []
}
```

**Body — EXPENSE** (bắt buộc có `attachments`)
```json
{
  "type": "EXPENSE",
  "projectId": 1,
  "phaseId": 2,
  "categoryId": 1,
  "amount": 3500000,
  "description": "Mua thiết bị testing",
  "attachments": [
    {
      "fileName": "invoice-04-2026.pdf",
      "cloudinaryPublicId": "requests/abc_xyz_123",
      "url": "https://res.cloudinary.com/.../upload/v.../requests/abc_xyz_123.pdf",
      "fileType": "application/pdf",
      "size": 245000
    }
  ]
}
```

**Body — REIMBURSE** (bắt buộc `advanceBalanceId` + `attachments`)
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
      "cloudinaryPublicId": "requests/abc_xyz_123",
      "url": "https://res.cloudinary.com/.../upload/v.../requests/abc_xyz_123.pdf",
      "fileType": "application/pdf",
      "size": 245000
    }
  ]
}
```

**Body — PROJECT_TOPUP**
```json
{
  "type": "PROJECT_TOPUP",
  "projectId": 1,
  "amount": 50000000,
  "description": "Xin cấp thêm vốn cho Phase 2"
}
```

**Body — DEPARTMENT_TOPUP**
```json
{
  "type": "DEPARTMENT_TOPUP",
  "amount": 200000000,
  "description": "Xin cấp vốn Q1/2026 cho phòng Engineering"
}
```

**Response:** `RequestDetailResponse` (như `GET /requests/:id`, `timeline: []`)

---

### `PUT /requests/:id`
Chỉnh sửa request. Điều kiện: `status = PENDING_APPROVAL`, chỉ requester (owner) được sửa.

Fields được phép sửa: `amount`, `description`, `attachments`.  
`attachments` là **ghi đè toàn bộ** — danh sách mới thay thế hoàn toàn danh sách cũ trong `request_attachments`.

**Body:**
```json
{
  "amount": 5000000,
  "description": "Updated description",
  "attachments": [
    {
      "fileName": "Travel_Itinerary.pdf",
      "cloudinaryPublicId": "requests/file_adv_001",
      "url": "https://res.cloudinary.com/.../upload/v.../requests/file_adv_001.pdf",
      "fileType": "application/pdf",
      "size": 156789
    },
    {
      "fileName": "Receipt_updated.jpg",
      "cloudinaryPublicId": "requests/file_adv_012",
      "url": "https://res.cloudinary.com/.../upload/v.../requests/file_adv_012.jpg",
      "fileType": "image/jpeg",
      "size": 89000
    }
  ]
}
```

**Response:** `RequestDetailResponse` (đầy đủ như `GET /requests/:id`)

---

### `DELETE /requests/:id`
Hủy request. Điều kiện: `status = PENDING_APPROVAL`, chỉ owner được hủy.

**Response:**
```json
{ "message": "Request cancelled successfully" }
```

> Backend set `requests.status = CANCELLED`. Tạo `request_histories`: `action = CANCEL`, `statusAfterAction = CANCELLED`.
