# Task 1 — Luồng truy vết cho người duyệt

> Triển khai chi tiết cho **A2 · Xem chi tiết theo vai trò** trong [da2-improvement-request.md](da2-improvement-request.md) và [da2-task-breakdown.md](da2-task-breakdown.md) (A2-1, A2-2, A2-3, A2-5).
> Phạm vi: **TEAM_LEADER · MANAGER · ACCOUNTANT · CFO** trên màn **chi tiết đề nghị** (request). Chi tiết dự án/giai đoạn (A2-4) không nằm trong task này.
> Lập ngày 2026-09-24.

---

## 1. Vấn đề

Người duyệt đang quyết định với **thông tin của riêng request đó**: số tiền, mô tả, chứng từ, lịch sử duyệt. Họ không thấy:

- **Người gửi là ai**: đang nợ tạm ứng bao nhiêu, đã xin những gì gần đây, tài khoản còn hoạt động không.
- **Nguồn tiền đang ra sao**: ngân sách danh mục, ví dự án còn khả dụng, quỹ phòng ban, quỹ công ty sau khi duyệt.
- **Có gì bất thường không**: yêu cầu trùng, dự án đã đóng, tài khoản ngân hàng vừa đổi.

Muốn biết, họ phải tự mở 3–4 màn hình khác. Một số thông tin thì không màn hình nào cho xem, ví dụ nợ tạm ứng của người khác hay số dư khả dụng của ví dự án.

## 2. Nguyên tắc

1. **Một endpoint, nhiều hình chiếu.** Thêm một endpoint `GET /requests/{id}/context` dùng chung cho mọi role. Lớp projection quyết định role nào thấy khối nào; không tạo endpoint riêng cho từng role.
2. **Ba khối thông tin chuẩn** (theo A2): **① Đối tượng**, **② Ngữ cảnh tài chính**, **③ Dấu vết**. Quyền quyết định độ sâu của khối ② và ③.
3. **Phạm vi dữ liệu không đổi.** Endpoint context kiểm tra quyền truy cập bằng đúng các query hiện có (`findDetailByIdForTl`, `findDetailByIdForManager`, `findDetailByIdForCfo`, `findDetailByIdForAccountant`). Ai không mở được chi tiết request thì cũng không mở được context.
4. **Không rời màn hình duyệt.** Mọi thông tin mở trong tab hoặc drawer trên cùng trang. Nút Duyệt / Từ chối luôn cố định ở cạnh dưới.
5. **Máy chỉ ra rủi ro, người quyết định.** Cờ cảnh báo chỉ hiển thị, không chặn nút duyệt. Chặn cứng vẫn do các validate hiện có đảm nhiệm, ví dụ không tự duyệt và không vượt quỹ.
6. **Theo convention trong CLAUDE.md.** Logic context đặt ở `RequestService` và gọi domain khác qua Service interface; không inject repository chéo domain. Ngưỡng cảnh báo đặt trong `application.yml`.

---

## 3. Ma trận vai trò × khối thông tin (A2-1)

✓ = thấy · — = không thấy. Cột "Nguồn" cho biết dữ liệu **đã có** trong response hoặc API hiện tại, hay **cần làm mới**.

| Khối | Thông tin | TL | MGR | ACC | CFO | Nguồn |
|---|---|:-:|:-:|:-:|:-:|---|
| ① | Request: số xin, số duyệt, loại, mô tả, danh mục | ✓ | ✓ | ✓ | ✓ | Có |
| ① | Người gửi: họ tên, mã NV, chức danh, email, phòng ban | ✓ | ✓ | ✓ | ✓ | Có (một phần FE chưa hiển thị) |
| ① | Người gửi: trạng thái tài khoản, vị trí và vai trò trong dự án | ✓ | ✓ | ✓ | — | Mới |
| ① | Người gửi: tài khoản ngân hàng nhận tiền | — | — | ✓ | — | Có |
| ② | Nợ tạm ứng của người gửi: tổng, từng khoản, tuổi nợ | ✓ | ✓ | ✓ | — | Mới |
| ② | Khoản tạm ứng gốc (REIMBURSE): số gốc, đã hoàn, còn lại, request gốc | ✓ | — | ✓ | — | Mới (đã load, chưa map) |
| ② | Ngân sách phase: hạn mức, đã chi, sau duyệt | ✓ | — | ✓ | — | Có |
| ② | Ngân sách **danh mục** của request: hạn mức, đã chi, sau duyệt | ✓ | — | ✓ | — | Mới |
| ② | Dự án: status, phase hiện tại, tổng / khả dụng / đã chi | ✓ | ✓ | ✓ | — | Một phần có |
| ② | Ví dự án: balance, locked, available | ✓ | ✓ | ✓ | — | Mới |
| ② | Ví phòng ban: khả dụng, sau duyệt, tổng đề xuất đang chờ | — | ✓ | — | ✓ | Một phần có |
| ② | Phòng ban: quota, các dự án và tỷ lệ đã chi, nợ tạm ứng của phòng ban | — | — | — | ✓ | Mới |
| ② | Quỹ công ty: số dư, sau duyệt, tổng đề xuất đang chờ, kỳ lương sắp chi | — | — | — | ✓ | Một phần có |
| ③ | Timeline duyệt đầy đủ, chứng từ | ✓ | ✓ | ✓ | ✓ | Có (MGR/CFO thiếu chứng từ) |
| ③ | Yêu cầu gần đây của người gửi | ✓ | ✓ | ✓ | — | Chỉ TL có |
| ③ | Yêu cầu cùng phase + danh mục gần đây | ✓ | — | ✓ | — | Mới |
| ③ | Các lần cấp vốn trước cho cùng dự án / phòng ban | — | ✓ | — | ✓ | Mới |
| ③ | Giao dịch và bút toán sinh ra từ request | — | — | ✓ | — | Mới (service có sẵn) |
| ③ | Thay đổi thông tin ngân hàng của người nhận gần đây | — | — | ✓ | — | Mới |
| — | Cờ cảnh báo (mục 5) | ✓ | ✓ | ✓ | ✓ | Mới |

> Chưa có ô "tuỳ". Muốn đổi ô nào thì sửa bảng này trước rồi mới sửa code.

---

## 4. Luồng xem theo từng role

### 4.1 TEAM_LEADER — duyệt ADVANCE / EXPENSE / REIMBURSE

**Màn:** `app/(dashboard)/team-leader/approvals/[id]/page.tsx`
**Câu hỏi người duyệt cần trả lời:** *Khoản này có hợp lý không, còn ngân sách không, và người này có đang ôm nợ tạm ứng không?*

Luồng xem:

1. Mở chi tiết. **Dải cảnh báo** ở đầu trang hiện các cờ, ví dụ `CATEGORY_BUDGET_EXCEEDED`, `REQUESTER_OVERDUE_ADVANCE`, `POSSIBLE_DUPLICATE`.
2. **Tab Người gửi**
   - Vị trí trong dự án, ngày tham gia, trạng thái tài khoản.
   - Tổng nợ tạm ứng và từng khoản còn nợ (mã, số còn lại, số ngày).
   - 10 yêu cầu gần nhất trong các dự án của TL.
   - Bấm một yêu cầu → drawer xem nhanh (mã, loại, số tiền, trạng thái, danh mục).
3. **Tab Ngân sách**
   - Ba tầng xếp chồng: **danh mục → phase → dự án**. Mỗi tầng có thanh "đã chi + yêu cầu này / hạn mức".
   - Ví dự án: available = balance − locked. Nếu available nhỏ hơn số tiền thì việc lock khi duyệt sẽ lỗi.
4. **Tab Lịch sử liên quan:** các yêu cầu cùng phase và danh mục trong 30 ngày, đánh dấu yêu cầu có cùng người gửi.
5. **Chỉ với REIMBURSE — khối "Khoản tạm ứng gốc"**
   - Mã request tạm ứng, số gốc, đã hoàn, còn lại, số còn lại sau khi duyệt.
   - Link mở drawer request tạm ứng gốc để so chứng từ.
6. Đọc xong → **Duyệt** (có thể giảm số tiền) hoặc **Từ chối**.

### 4.2 MANAGER — duyệt PROJECT_TOPUP

**Màn:** `app/(dashboard)/manager/approvals/[id]/page.tsx`
**Câu hỏi:** *Dự án có thật sự cần thêm vốn không, vốn đã cấp trước đó được dùng thế nào, và ví phòng ban có đủ cho cả những đề xuất khác đang chờ?*

1. **Dải cảnh báo**, ví dụ `TOPUP_WHILE_BUDGET_UNUSED`, `QUEUE_EXCEEDS_DEPT_WALLET`, `PROJECT_NOT_ACTIVE`.
2. **Tab Dự án**
   - Status, phase hiện tại.
   - Bảng phase: hạn mức, đã chi, % sử dụng.
   - Ví dự án (balance / locked / available). Tiền đang bị lock cho các yêu cầu Flow 1 đã duyệt nhưng chưa giải ngân.
3. **Tab Người gửi (TL):** dư nợ tạm ứng, số dự án phụ trách, số yêu cầu đang chờ, các lần xin vốn trước.
4. **Tab Lịch sử cấp vốn**
   - Mọi PROJECT_TOPUP của dự án này: ngày, số xin, số duyệt, trạng thái.
   - Dòng tổng: **đã cấp** so với **đã chi** kể từ lần cấp đầu tiên.
5. **Khối Ví phòng ban**
   - Khả dụng hiện tại.
   - Tổng các PROJECT_TOPUP khác đang chờ trong phòng ban.
   - Khả dụng sau khi duyệt yêu cầu này.
6. **Chứng từ đính kèm**, nếu có. Hiện backend chưa map ra DTO.
7. **Duyệt** (tiền chuyển ngay) hoặc **Từ chối**.

### 4.3 ACCOUNTANT — giải ngân Flow 1

**Màn:** `app/(dashboard)/accountant/disbursements/[id]/page.tsx`
**Câu hỏi:** *Chuỗi duyệt có hợp lệ không, tiền có đang thật sự được giữ trong ví dự án không, và có đang chuyển tiền đúng người, đúng tài khoản không?*

1. **Dải cảnh báo**, ví dụ `BANK_INFO_RECENTLY_CHANGED`, `FUNDS_NOT_LOCKED`, `APPROVED_AMOUNT_REDUCED`, `POSSIBLE_DUPLICATE`.
2. **Checklist hiện có (5 mục).** Mỗi mục có link "xem căn cứ" mở đúng tab bên dưới:
   - "Số tiền khớp chứng từ" → tab Chứng từ.
   - "Ngân sách phase còn đủ" → tab Ngân sách.
   - "Chuỗi phê duyệt đầy đủ" → tab Dấu vết.
3. **Tab Người nhận**
   - Tài khoản ngân hàng (đã có), trạng thái tài khoản.
   - **Lần đổi thông tin ngân hàng gần nhất**: thời gian, giá trị cũ đã che bớt.
   - Nợ tạm ứng hiện tại.
4. **Tab Ngân sách**
   - Danh mục / phase / dự án.
   - Xác nhận ví dự án **đang lock ≥ số được duyệt** cho request này.
5. **Tab Dấu vết**
   - Toàn bộ timeline: ai duyệt, lúc nào, ghi chú.
   - **Số xin → số TL duyệt** (nếu khác thì làm nổi bật).
   - Với request đã PAID: giao dịch và bút toán kép, link sang `/accountant/ledger/{transactionId}`.
6. **Chỉ với REIMBURSE:** khoản tạm ứng gốc và số còn lại sau khi quyết toán.
7. Nhập PIN → **Giải ngân**, hoặc **Từ chối**.

### 4.4 CFO — duyệt DEPARTMENT_TOPUP

**Màn:** `app/(dashboard)/cfo/approvals/[id]/page.tsx`
**Câu hỏi:** *Phòng ban có thật sự thiếu vốn không, và quỹ công ty còn đủ cho các phòng ban khác và cho kỳ lương sắp tới không?*

1. **Dải cảnh báo**, ví dụ `PAYROLL_AT_RISK`, `QUEUE_EXCEEDS_COMPANY_FUND`, `DEPT_HIGH_OUTSTANDING_ADVANCE`.
2. **Tab Phòng ban**
   - Tổng quota, ví phòng ban khả dụng.
   - Các dự án: status, tổng ngân sách, đã chi, % sử dụng.
   - Nợ tạm ứng của phòng ban: tổng và số nhân viên đang nợ.
3. **Tab Lịch sử cấp quota:** mọi DEPARTMENT_TOPUP của phòng ban này (ngày, số xin, số duyệt, trạng thái) và tổng đã cấp trong 90 ngày.
4. **Khối Quỹ công ty**
   - Số dư hiện tại.
   - Tổng các DEPARTMENT_TOPUP đang chờ của mọi phòng ban.
   - **Kỳ lương sắp chi**: tổng thực lĩnh của kỳ DRAFT, hoặc ước tính bằng kỳ COMPLETED gần nhất.
   - Số dư sau khi duyệt.
5. **Người gửi (Manager)** và **chứng từ**, nếu có. Hiện backend chưa fetch chứng từ.
6. **Duyệt** hoặc **Từ chối**.

---

## 5. Danh mục cờ cảnh báo

Mức độ: 🔴 `CRITICAL` — nên dừng lại kiểm tra · 🟠 `WARNING` — cần xem · 🔵 `INFO` — để biết.
Ngưỡng đặt trong `application.yml` dưới nhóm `app.request.trace.*`.

| Code | Mức | Điều kiện | Role thấy |
|---|---|---|---|
| `CATEGORY_BUDGET_EXCEEDED` | 🟠 | danh mục: `currentSpent + amount > budgetLimit` | TL, ACC |
| `CATEGORY_BUDGET_MISSING` | 🟠 | phase chưa có dòng `phase_category_budgets` cho danh mục này | TL, ACC |
| `PHASE_BUDGET_EXCEEDED` | 🟠 | phase: `currentSpent + amount > budgetLimit` | TL, ACC |
| `PROJECT_WALLET_INSUFFICIENT` | 🔴 | ví dự án `available < amount` (ADVANCE/EXPENSE, trạng thái PENDING) | TL |
| `FUNDS_NOT_LOCKED` | 🔴 | ví dự án `lockedBalance < approvedAmount` khi ở bước giải ngân | ACC |
| `PROJECT_NOT_ACTIVE` | 🟠 | project status ≠ ACTIVE, hoặc phase status ≠ ACTIVE | TL, MGR, ACC |
| `REQUESTER_NOT_MEMBER` | 🔴 | người gửi không còn là thành viên dự án | TL, ACC |
| `REQUESTER_LOCKED` | 🔴 | tài khoản người gửi không ACTIVE | TL, ACC |
| `REQUESTER_OVERDUE_ADVANCE` | 🟠 | có khoản tạm ứng chưa SETTLED quá `overdue-advance-days` (mặc định 30) | TL, MGR, ACC |
| `POSSIBLE_DUPLICATE` | 🟠 | cùng người gửi, cùng danh mục, số tiền lệch ≤ `duplicate-amount-tolerance-percent`, trong `duplicate-window-days` (mặc định 7), chưa bị REJECTED/CANCELLED | TL, ACC |
| `REIMBURSE_EXCEEDS_REMAINING` | 🔴 | REIMBURSE `amount > advanceBalance.remainingAmount` | TL, ACC |
| `APPROVED_AMOUNT_REDUCED` | 🔵 | `approvedAmount < amount` | ACC |
| `BANK_INFO_MISSING` | 🔴 | người nhận chưa có ngân hàng / số tài khoản / chủ tài khoản | ACC |
| `BANK_INFO_RECENTLY_CHANGED` | 🔴 | thông tin ngân hàng đổi trong `bank-change-window-days` (mặc định 7) | ACC |
| `TOPUP_WHILE_BUDGET_UNUSED` | 🔵 | `project.availableBudget / totalBudget ≥ unused-budget-ratio` (mặc định 0.5) | MGR |
| `DEPT_WALLET_INSUFFICIENT` | 🔴 | ví phòng ban `available < amount` | MGR |
| `QUEUE_EXCEEDS_DEPT_WALLET` | 🟠 | tổng PROJECT_TOPUP đang chờ của phòng ban > ví phòng ban khả dụng | MGR |
| `COMPANY_FUND_INSUFFICIENT` | 🔴 | quỹ công ty `< amount` | CFO |
| `QUEUE_EXCEEDS_COMPANY_FUND` | 🟠 | tổng DEPARTMENT_TOPUP đang chờ > quỹ công ty | CFO |
| `PAYROLL_AT_RISK` | 🔴 | `quỹ − amount < kỳ lương sắp chi` | CFO |
| `DEPT_HIGH_OUTSTANDING_ADVANCE` | 🟠 | nợ tạm ứng của phòng ban ≥ `dept-outstanding-ratio` × ví phòng ban (mặc định 0.3) | CFO |

```yaml
# application.yml
app:
  request:
    trace:
      overdue-advance-days: 30
      duplicate-window-days: 7
      duplicate-amount-tolerance-percent: 5
      bank-change-window-days: 7
      unused-budget-ratio: 0.5
      dept-outstanding-ratio: 0.3
      recent-requests-limit: 10
      same-category-lookback-days: 30
      topup-history-limit: 10
```

---

## 6. Backend

### 6.1 Endpoint

```
GET /api/v1/requests/{id}/context
→ ResponseEntity<ApiResponse<RequestContextResponse>>
```

- **Controller:** `RequestController` (đã có base path `/requests`).
- **Service:** `RequestService.getRequestContext(Long requestId, UserDetailsAdapter caller)`.
- **Phân quyền:** `@PreAuthorize("hasAnyAuthority('REQUEST_APPROVE_TEAM_LEADER','REQUEST_APPROVE_PROJECT_TOPUP','REQUEST_PAYOUT','REQUEST_APPROVE_DEPT_TOPUP')")` ở ServiceImpl. Sau đó tải request bằng đúng query `findDetailByIdFor{Role}` của role người gọi. Không tìm thấy → `ResourceNotFoundException` (không tiết lộ request có tồn tại hay không).
- Nếu người gọi có nhiều quyền duyệt, chọn **lát cắt theo loại request**: Flow 1 → TL hoặc ACC tùy status; PROJECT_TOPUP → MGR; DEPARTMENT_TOPUP → CFO.

### 6.2 Response

Một khối không được phép xem thì là `null`; FE ẩn tab tương ứng. Tiền dùng `BigDecimal`.

```jsonc
{
  "viewer": "TEAM_LEADER",                 // lát cắt đã áp dụng
  "requester": {
    "id": 12, "fullName": "…", "employeeCode": "MK004", "jobTitle": "…",
    "departmentName": "IT", "status": "ACTIVE",
    "projectRole": "MEMBER", "position": "Backend", "joinedAt": "2026-03-02",
    "bank": null,                          // ACC: { bankName, accountNumMasked, owner, lastChangedAt }
    "recentRequests": [ { "id", "requestCode", "type", "amount", "status", "categoryName", "createdAt" } ]
  },
  "advance": {
    "totalOutstanding": 3500000,
    "items": [ { "id", "advanceRequestCode", "originalAmount", "remainingAmount", "status", "ageDays" } ],
    "linked": null                         // REIMBURSE: { id, advanceRequestId, advanceRequestCode, originalAmount, reimbursedAmount, returnedAmount, remainingAmount, remainingAfterApproval }
  },
  "budget": {
    "project":  { "id", "status", "totalBudget", "availableBudget", "totalSpent", "currentPhaseName" },
    "phase":    { "id", "status", "budgetLimit", "currentSpent", "afterApproval" },
    "category": { "id", "name", "budgetLimit", "currentSpent", "remaining", "afterApproval" },
    "projectWallet": { "balance", "lockedBalance", "availableBalance" },
    "department":    null,                 // MGR/CFO: { id, name, code, totalProjectQuota, walletAvailable, afterApproval, pendingTopupTotal, outstandingAdvance, employeesWithDebt, projects: [...] }
    "companyFund":   null                  // CFO: { balance, afterApproval, pendingTopupTotal, upcomingPayroll: { periodCode, status, totalNet, estimated } }
  },
  "history": {
    "sameCategoryRecent": [ { "id", "requestCode", "requesterName", "amount", "status", "createdAt", "sameRequester" } ],
    "previousTopups": null                 // MGR/CFO: [ { id, requestCode, amount, approvedAmount, status, createdAt } ], kèm totalGranted, totalSpentSinceFirst
  },
  "trace": {
    "transactions": null,                  // ACC & status PAID: [ { transactionId, transactionCode, type, amount, createdAt, entries: [ { direction, walletOwnerType, ownerName, amount, balanceAfter } ] } ]
    "bankChanges": null                    // ACC: [ { changedAt, actorName, fields: ["bankAccountNum"] } ]
  },
  "flags": [ { "code": "CATEGORY_BUDGET_EXCEEDED", "severity": "WARNING", "message": "…", "data": { "limit": 5000000, "after": 6200000 } } ]
}
```

Timeline và attachments vẫn nằm ở response chi tiết hiện tại. **Không** lặp lại trong context.

### 6.3 Lớp projection

- `modules/request/service/context/RequestContextProjector.java` là nơi duy nhất quyết định khối nào được bật, dựa trên `viewer` và bảng ở mục 3. Thêm một role mới thì chỉ sửa class này, không sửa controller (đúng tiêu chí A2-2).
- `modules/request/service/context/RequestFlagEvaluator.java` tính cờ từ dữ liệu đã gom, đọc ngưỡng qua `@Value("${app.request.trace.*}")`.
- `modules/request/mapper/RequestContextMapper.java` làm entity → response; không viết mapper private trong service.

### 6.4 Dữ liệu cần thêm (qua Service interface của domain sở hữu)

| Cần | Domain | Method đề xuất | Dựa trên |
|---|---|---|---|
| Khoản tạm ứng còn nợ của một user | request (cùng domain) | repo `AdvanceBalanceRepository.findByUserIdAndStatusNot` | Có sẵn |
| Nợ tạm ứng của phòng ban | request | `sumOutstandingByDeptId`, `countEmployeesWithDebtByDeptId` | Có sẵn |
| Yêu cầu gần đây của người gửi | request | `RequestSpecification.hasRequester` + phân trang | Có sẵn |
| Yêu cầu cùng phase + danh mục | request | **thêm** predicate `hasPhase`, `hasCategory`, `createdAfter` vào `RequestSpecification`, và method `filterSameCategory(...)` | Mới |
| Lịch sử top-up theo dự án / phòng ban | request | `hasType` + **thêm** `hasProject`, `hasRequesterDepartment` | Mới một phần |
| Ngân sách danh mục của (phase, category) | project | **thêm** `CategoryBudgetService.getBudget(phaseId, categoryId)` → Optional | `PhaseCategoryBudgetRepository.findById` |
| Ví dự án / phòng ban / quỹ công ty | wallet | `WalletService.getWallet(ownerType, ownerId)` | Có sẵn |
| Giao dịch theo request | wallet | `WalletService.getTransactionsByReference(REQUEST, id)` | Có sẵn, chưa có endpoint dùng |
| Thành viên dự án (role, vị trí, ngày tham gia) | project | **thêm** `ProjectQueryService.getMembership(projectId, userId)` | `ProjectMemberRepository` |
| Kỳ lương sắp chi | accounting | **thêm** `PayrollManagementService.getUpcomingPayrollEstimate()` | `PayrollPeriodRepository` |
| Lần đổi thông tin ngân hàng | audit | **thêm** `AuditLogService.findFieldChanges(entityName, entityId, fields, since)` và predicate `hasEntityId` trong `AuditLogSpecification` | `audit_logs` (old/new JSONB) |
| Ngân hàng người nhận | profile | `ProfileService.getProfileByUserId` | Có sẵn |

> Không copy pattern của `TeamLeaderProjectServiceImpl` / `ManagerProjectServiceImpl`: hai class này đang inject `RequestRepository` và `WalletRepository` trực tiếp.

### 6.5 Sửa kèm

- **REIMBURSE:** map `advanceBalance` (đã được `findDetailByIdForAccountant` fetch) ra DTO chi tiết. Thêm `linkedAdvance` vào response TL và ACC.
- **PROJECT_TOPUP:** map `attachments` trong `toManagerApprovalDetailResponse`; query đã fetch sẵn.
- **DEPARTMENT_TOPUP:** thêm fetch attachments vào `findDetailByIdForCfo` và map ra response.
- **`debtBalance`** ở `TeamMemberDetailResponse` và `DepartmentMemberDetailResponse` đang là `lockedBalance` của ví, không phải nợ tạm ứng. Đổi sang `sumRemainingByUserId`, và đổi luôn label trên FE.
- Không cần migration: task này không đổi schema. Nếu query "cùng phase + danh mục" chậm thì thêm index `requests(phase_id, category_id, created_at)` bằng migration mới.

---

## 7. Frontend

### 7.1 Component dùng chung — `components/approval/`

| Component | Vai trò |
|---|---|
| `RiskFlagsBar` | Dải cờ ở đầu trang, sắp CRITICAL → WARNING → INFO. Bấm một cờ thì cuộn tới tab chứa căn cứ |
| `TracePanel` | Khung tab: *Người gửi · Ngân sách · Lịch sử liên quan · Dấu vết*. Tự ẩn tab khi khối tương ứng là `null` |
| `BudgetStack` | Ba tầng danh mục → phase → dự án, mỗi tầng một thanh "đã chi + yêu cầu này / hạn mức" |
| `AdvanceDebtList` | Danh sách khoản tạm ứng còn nợ, tô màu theo tuổi nợ |
| `LinkedAdvanceCard` | Khoản tạm ứng gốc của REIMBURSE, có số còn lại sau duyệt |
| `RequestQuickDrawer` | Drawer xem nhanh một request khác, không rời trang |
| `TopupHistoryTable` | Lịch sử cấp vốn / cấp quota kèm tổng đã cấp và đã chi |
| `FundImpactCard` | Quỹ / ví trước và sau khi duyệt, có tổng đề xuất đang chờ và kỳ lương sắp chi (CFO) |
| `lib/api/request-context.ts` | `getRequestContext(id)` + type `RequestContext` trong `types/request.ts` |

Trang duyệt gọi song song `GET …/{id}` (chi tiết, như hiện tại) và `GET /requests/{id}/context`. Context lỗi → trang vẫn duyệt được, chỉ hiện "Không tải được thông tin truy vết" ở khung panel.

### 7.2 Quick wins — chỉ sửa FE, không chờ backend

| Màn | Việc |
|---|---|
| TL `team-leader/approvals/[id]` | Hiện `requester.jobTitle`, `rejectReason`. Gọi thêm `GET /team-leader/team-members/{requesterId}` (yêu cầu gần đây) và `GET /team-leader/projects/{projectId}/categories?phaseId=` (ngân sách danh mục) |
| MGR `manager/approvals/[id]` | Hiện `requester.email`, `requester.departmentName`, `approvedAmount`, `rejectReason`. Gọi thêm `GET /manager/projects/{projectId}` (phase, trạng thái) và `GET /manager/department/members/{requesterId}` |
| ACC `accountant/disbursements/[id]` | Hiện **toàn bộ timeline** thay vì chỉ bước TL; hiện `amount` (số xin) cạnh `approvedAmount`; hiện `categoryName` |
| CFO `cfo/approvals/[id]` | Hiện `department.totalProjectQuota`; bỏ các fallback không tồn tại `systemFund.totalBalance`, `companyFund.availableBalance`; gọi thêm `GET /cfo/approvals?status=PENDING` để cộng tổng đề xuất đang chờ |

Khi endpoint context xong, các lời gọi bổ sung ở bảng trên được thay bằng context.

---

## 8. Chia task

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **T1-0** | Quick wins FE (mục 7.2) | S · 1 | — | 4 màn hiện đủ field đã có; không còn fallback chết ở trang CFO |
| **T1-1** | Chốt ma trận mục 3 và danh mục cờ mục 5 với GVHD (= A2-1) | S · 1 | — | Hai bảng được duyệt, không còn ô "tuỳ" |
| **T1-2** | `RequestContextResponse` + `RequestContextProjector` + endpoint `GET /requests/{id}/context`, chưa có cờ (= A2-2) | M · 2 | T1-1 | Mỗi role gọi và nhận đúng khối theo ma trận; thêm role giả không phải sửa controller |
| **T1-3** | Các method dữ liệu mục 6.4 (service interface + impl + spec predicate) | M · 2.5 | T1-2 | Mỗi khối trong response có dữ liệu thật trên seed |
| **T1-4** | `RequestFlagEvaluator` + cấu hình `app.request.trace.*` | M · 1.5 | T1-3 | 21 cờ có unit test cho điều kiện bật / tắt |
| **T1-5** | Sửa kèm mục 6.5 (linked advance, attachments top-up, `debtBalance`) | S · 1 | — | REIMBURSE hiện khoản gốc; MGR/CFO thấy chứng từ; dư nợ khớp `advance_balances` |
| **T1-6** | Component dùng chung mục 7.1 | M · 2 | T1-2 (chốt type) | Một trang demo nội bộ hiển thị đủ component với dữ liệu mẫu |
| **T1-7** | Ghép vào 4 màn duyệt, thay các lời gọi quick-win bằng context (= A2-3) | M · 2 | T1-4, T1-6 | 4 role mở cùng loại request thấy đúng lát cắt của mình; context lỗi vẫn duyệt được |
| **T1-8** | Test rò rỉ quyền (= A2-5): TL khác dự án, Manager khác phòng ban, ACC với request chưa TL duyệt, CFO với PROJECT_TOPUP, EMPLOYEE, ADMIN | S · 1 | T1-2 | Mọi trường hợp ngoài phạm vi trả 404, không lộ khối nào |
| **T1-9** | Bổ sung `DataSeeder` để demo được cờ: một yêu cầu trùng, một nợ tạm ứng quá 30 ngày, một lần đổi ngân hàng gần đây, một phòng ban có hàng chờ vượt ví | S · 0.5 | T1-4 | Demo đủ cờ CRITICAL / WARNING mà không cần sửa DB bằng tay |
| | **Tổng** | **~14.5** | | |

> So với ước lượng A2 ban đầu (9 ngày, gồm cả A2-4): task này dài hơn khoảng 5.5 ngày vì thêm cờ cảnh báo (T1-4), quick wins (T1-0) và seed demo (T1-9). Nếu thiếu thời gian, cắt theo thứ tự **T1-9 → bớt cờ INFO → T1-0** (T1-0 chỉ cần nếu muốn có kết quả sớm trước khi context xong).

### Thứ tự đề xuất

```
T1-0 (làm ngay, độc lập)
T1-1 ──► T1-2 ──► T1-3 ──► T1-4 ──► T1-7 ──► T1-9
            │                         ▲
            ├──► T1-6 ────────────────┘
            └──► T1-8
T1-5 (song song bất kỳ lúc nào)
```

---

## 9. Không nằm trong task này

- Chi tiết dự án / giai đoạn theo vai trò (A2-4).
- Chặn cứng thao tác duyệt dựa trên cờ: cờ chỉ để cảnh báo.
- Đối chiếu nội dung chứng từ bằng OCR (F1). Kết quả F1 sẽ hiện ở tab **Dấu vết** của `TracePanel` (F1-6).
- Mở quyền `AUDIT_LOG_VIEW` cho người duyệt. Chỉ trả về đúng thay đổi ngân hàng của người nhận, đã che bớt số tài khoản, qua endpoint context.
