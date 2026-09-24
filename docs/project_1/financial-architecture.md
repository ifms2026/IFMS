# Financial Architecture — IFMS

## 4-Tier Wallet Model

```
External Bank
    ↓ SYSTEM_TOPUP                          ← boundary: FLOAT_MAIN +amount
CompanyFund Wallet (WalletOwnerType.COMPANY_FUND)
    ↓ DEPT_QUOTA_ALLOCATION  (CFO approves DEPARTMENT_TOPUP)
Department Wallet (WalletOwnerType.DEPARTMENT)
    ↓ PROJECT_QUOTA_ALLOCATION  (Manager approves PROJECT_TOPUP)
Project Wallet (WalletOwnerType.PROJECT)
    ↓ REQUEST_PAYMENT  (Accountant pays out ADVANCE/EXPENSE)
User Wallet (WalletOwnerType.USER)
    ↓ ADVANCE_RETURN  (nhân viên hoàn trả tạm ứng dư)
Project Wallet
```

### Wallet Model

```java
Wallet { ownerType, ownerId, balance, lockedBalance, version }
// availableBalance = balance - lockedBalance
// lock()   → reservations (request approved, awaiting payout)
// settle() → finalize locked → debit
// debit()  → direct debit (no prior reservation)
// credit() → money in
```

### Double-Entry Ledger

Mỗi `Transaction` tạo đúng 2 `LedgerEntry` (DEBIT + CREDIT).
`LedgerEntry` là source of truth cho wallet history và balance reconstruction.
Entries **không bao giờ UPDATE/DELETE** — corrections dùng REVERSAL transaction.

**Ngoại lệ — Boundary transactions** (tiền vượt ranh giới hệ thống):
`SYSTEM_TOPUP` tạo đúng **1 LedgerEntry** (CREDIT, CompanyFund wallet).
Phía external bank không có wallet trong IFMS nên không có DEBIT entry.
Reversal của boundary transaction cũng chỉ tạo 1 entry (DEBIT).

---

## CompanyFund vs SystemFund (thay đổi v12)

### Trước (cũ)
`SystemFund` entity có `totalBalance`, `debit()`, `credit()` — tự track balance ngoài
`WalletService`. Không có LedgerEntry → không có audit trail cho quỹ hệ thống.
`Wallet(SYSTEM_FUND)` tồn tại trong enum nhưng **không được seed** → DEPT_QUOTA_ALLOCATION
sẽ fail với `ResourceNotFoundException`.

### Sau (hiện tại)

**`CompanyFund` entity** — metadata-only (singleton, id=1):
```java
CompanyFund {
    bankName, bankAccount       // tài khoản ngân hàng công ty
    externalBankBalance         // số dư theo sao kê NH (nhập tay bởi Accountant)
    lastStatementDate           // ngày sao kê
    lastStatementUpdatedBy      // userId người cập nhật
}
```
Không có `totalBalance`, `debit()`, `credit()`. Balance được track hoàn toàn
bởi `Wallet(COMPANY_FUND, ownerId=1)` thông qua `WalletService`.

**`Wallet(COMPANY_FUND, ownerId=1)`** — authoritative balance:
- Tăng khi: `SYSTEM_TOPUP` (nạp từ ngân hàng), `ADVANCE_RETURN` (user hoàn tạm ứng về project... thực ra về project, không về company fund)
- Giảm khi: `DEPT_QUOTA_ALLOCATION`, `PAYSLIP_PAYMENT`

---

## FLOAT_MAIN — System-Wide Control Wallet

`Wallet(FLOAT_MAIN, ownerId=0)` — wallet đặc biệt, không có owner entity thực.

### Invariant bất biến
```
FLOAT_MAIN.balance = SUM(balance của tất cả wallet WHERE ownerType != FLOAT_MAIN)
```

### Quy tắc cập nhật
FLOAT_MAIN **chỉ thay đổi** khi tiền vượt ranh giới hệ thống (external ↔ IFMS):

| Transaction | FLOAT_MAIN | Lý do |
|-------------|-----------|-------|
| `SYSTEM_TOPUP` | **+amount** | Tiền mới vào hệ thống từ ngân hàng |
| `DEPOSIT` *(future)* | **+amount** | Tiền mới vào từ ví cá nhân user |
| `WITHDRAW` *(future)* | **−amount** | Tiền rời hệ thống về tài khoản user |
| `DEPT_QUOTA_ALLOCATION` | **0** | Chỉ chuyển nội bộ |
| `PROJECT_QUOTA_ALLOCATION` | **0** | Chỉ chuyển nội bộ |
| `REQUEST_PAYMENT` | **0** | Chỉ chuyển nội bộ |
| `PAYSLIP_PAYMENT` | **0** | Chỉ chuyển nội bộ |
| `ADVANCE_RETURN` | **0** | Chỉ chuyển nội bộ |
| `REVERSAL` | **đảo chiều của giao dịch gốc** | Chỉ nếu gốc là boundary |

FLOAT_MAIN **không có LedgerEntry** — nó là balance-only control wallet, không tham
gia double-entry ledger.

### Discrepancy Detection
```
discrepancy = FLOAT_MAIN.balance − SELECT SUM(balance) FROM wallets WHERE owner_type != 'FLOAT_MAIN'

= 0  →  Hệ thống toàn vẹn
≠ 0  →  Có mutation xảy ra ngoài WalletService (direct DB, bug...)
```
Endpoint: `GET /api/v1/company-fund/reconciliation`

---

## Reconciliation Report

Endpoint `GET /api/v1/company-fund/reconciliation` trả về `ReconciliationReportDto`:

```
┌─────────────────────────────────────────────────────────┐
│ SYSTEM INTEGRITY CHECK (FLOAT_MAIN invariant)           │
│   floatMainBalance     = Wallet(FLOAT_MAIN).balance     │
│   computedWalletSum    = SUM(tất cả ví trừ FLOAT_MAIN)  │
│   systemDiscrepancy    = floatMain − computedSum        │ ← kỳ vọng = 0
│   systemIntegrityValid = (discrepancy == 0)             │
├─────────────────────────────────────────────────────────┤
│ WALLET BREAKDOWN (tiền đang ở đâu trong IFMS)           │
│   companyFundBalance   = Wallet(COMPANY_FUND).balance   │
│   totalDeptWallets     = Σ Wallet(DEPARTMENT)           │
│   totalProjectWallets  = Σ Wallet(PROJECT)              │
│   totalUserWallets     = Σ Wallet(USER)                 │
├─────────────────────────────────────────────────────────┤
│ BANK STATEMENT CHECK (external reconciliation)          │
│   externalBankBalance  = từ sao kê NH (nhập tay)        │
│   bankDiscrepancy      = companyFund − externalBank     │ ← kỳ vọng = 0
└─────────────────────────────────────────────────────────┘
```

---

## AdvanceBalance Lifecycle

Mỗi ADVANCE payout tạo 1 `AdvanceBalance` record.
Giảm bằng 2 cách:

1. **Reimburse** — REIMBURSE request approved → không có wallet movement, chỉ update AdvanceBalance
2. **Return Cash** — cash returned → ADVANCE_RETURN transaction, rút tiền từ USER wallet → PROJECT wallet

---

## Request Approval Flows (Segregation of Duties)

### Flow 1 — Personal Expense (ADVANCE / EXPENSE / REIMBURSE)
Nguy hiểm cao (chứng từ gốc, dễ gian lận) → vẫn kiểm soát bằng workflow duyệt và audit trail

```
Member tạo request (PENDING)
    ↓
TEAM_LEADER duyệt (DECISION)
    ↓
APPROVED_BY_TEAM_LEADER   ← Accountant nhìn thấy tại /accountant/disbursements
    ↓
PAID
```

**Trách nhiệm:**
- TEAM_LEADER: Quyết định approve/reject
- ACCOUNTANT: Thực hiện giải ngân (execute payout) — không quyết định, chỉ execute

**Wallet operation:** `walletService.settleAndTransfer(PROJECT → USER, REQUEST_PAYMENT)`

---

### Flow 2 — Project Fund Top-up (PROJECT_TOPUP)
Nguy hiểm trung bình (internal allocation) → **Auto-pay** sau Manager approve

```
TEAM_LEADER request vốn (PENDING)
    ↓
MANAGER duyệt (DECISION)
    ↓
APPROVED_BY_MANAGER
    ↓
[Scheduler auto-pay 1 phút sau]
    ↓
PAID (Department → Project transfer)
```

**Trách nhiệm:**
- MANAGER: Quyết định cấp vốn hay không
- ACCOUNTANT: Chỉ ghi sổ (post-facto)

**Wallet operation:** `walletService.transfer(DEPARTMENT → PROJECT, PROJECT_QUOTA_ALLOCATION)`

---

### Flow 3 — Department Quota Top-up (DEPARTMENT_TOPUP)
Nguy hiểm thấp (strategic decision) → **Auto-pay** sau CFO approve

```
MANAGER request quota (PENDING)
    ↓
CFO duyệt (DECISION)
    ↓
APPROVED_BY_CFO
    ↓
[Scheduler auto-pay 1 phút sau]
    ↓
PAID (CompanyFund → Department transfer)
```

**Trách nhiệm:**
- CFO: Quyết định cấp quota phòng ban
- ACCOUNTANT: Chỉ ghi sổ (post-facto)

**Wallet operation:** `walletService.transfer(COMPANY_FUND → DEPARTMENT, DEPT_QUOTA_ALLOCATION)`

---

### Flow 4 — System Fund Top-up (SYSTEM_TOPUP)
Nạp tiền từ ngân hàng thực tế vào quỹ công ty.

```
Accountant/CFO xác nhận tiền đã về từ ngân hàng
    ↓
POST /api/v1/company-fund/topup
    ↓
WalletService.systemTopup(amount, paymentRef, description)
    ↓
credit(CompanyFund wallet) + LedgerEntry(CREDIT) + FLOAT_MAIN +amount
    ↓
PUT /api/v1/company-fund/bank-statement  (optional: cập nhật sao kê NH)
```

**Wallet operation:** `walletService.systemTopup(amount, paymentRef, description)`
*(boundary operation — 1 LedgerEntry, không có source wallet trong IFMS)*

---

## Request Status Enum

```java
public enum RequestStatus {
    // ─ ADVANCE/EXPENSE/REIMBURSE ─
    PENDING,                        // Member vừa tạo
    APPROVED_BY_TEAM_LEADER,        // TL duyệt, chờ Accountant execute payout
    PAID,                           // Đã giải ngân
    
    // ─ PROJECT_TOPUP ─
    APPROVED_BY_MANAGER,            // Manager duyệt, chờ auto-pay
    
    // ─ DEPARTMENT_TOPUP ─
    APPROVED_BY_CFO,                // CFO duyệt, chờ auto-pay
    
    // ─ Common ─
    REJECTED,                       // Reject tại decision stage
    CANCELLED                       // Cancel sau khi đã approve
}
```

---

## Segregation of Duties (SoD) Principle

| Loại request | Decision (duyệt) | Execution (giải ngân) |
|---|---|---|
| ADVANCE/EXPENSE/REIMBURSE | TEAM_LEADER | Theo workflow thanh toán ✅ |
| PROJECT_TOPUP | MANAGER | Scheduler (auto) ✅ |
| DEPARTMENT_TOPUP | CFO | Scheduler (auto) ✅ |
| SYSTEM_TOPUP | CFO/Accountant | Ngay lập tức (1-step) ✅ |

**Nguyên tắc:** Người decide ≠ Người execute → chống gian lận, dễ audit

---

## WalletOwnerType Summary

| Type | ownerId | Ý nghĩa |
|------|---------|---------|
| `USER` | userId | Ví cá nhân nhân viên |
| `DEPARTMENT` | departmentId | Ví phòng ban |
| `PROJECT` | projectId | Ví dự án |
| `COMPANY_FUND` | 1 (singleton) | Quỹ công ty — tiền còn lại sau cấp phát |
| `FLOAT_MAIN` | 0 (sentinel) | Control wallet — invariant checker, không có owner thực |
