# Accountant Disbursement — Implementation Plan

> **Scope:** `GET /accountant/disbursements`, `GET /accountant/disbursements/:id`,
> `POST /accountant/disbursements/:id/disburse`, `POST /accountant/disbursements/:id/reject`
>
> **Source of truth:** `docs/API_Spec.md` lines 2606–2797, `docs/financial-architecture.md`

---

## 1. Codebase Analysis

### What already exists

| Component | Status |
|---|---|
| `WalletService.settleAndTransfer` | ✅ Implemented |
| `WalletService.lockFunds` / `unlockFunds` | ✅ Implemented |
| `PinValidator` | ✅ Implemented |
| `ProfileService.verifyMyPin` | ✅ Implemented |
| `TransactionType.REQUEST_PAYMENT` | ✅ Exists in enum |
| `AdvanceBalance` entity + `AdvanceBalanceRepository` | ✅ Exists |
| `PhaseCategoryBudgetRepository` | ✅ Exists (project module) |
| `CategoryBudgetService` | ✅ Interface exists (untracked) |
| `TeamLeaderApprovalController`, `ManagerApprovalController` | ✅ Exist as pattern reference |
| `AttachmentResponse` | ✅ Reusable |
| `RejectRequestRequest` | ✅ Reusable for accountant reject |

### Critical Gap — `lockFunds` Missing from TL Approval

`RequestServiceImpl.approveTlRequest` currently sets `status = APPROVED_BY_TEAM_LEADER` but
**does NOT call `walletService.lockFunds`**. The `settleAndTransfer` operation calls `wallet.settle(amount)`
which decrements `lockedBalance`. If no prior lock was made, `settle()` will throw or produce a
negative `lockedBalance`.

**Fix required in `approveTlRequest` before implementing disburse.**

### Discrepancies Between API Spec and Codebase

| Spec field | Codebase reality | Resolution |
|---|---|---|
| `categoryCode: "CAT-EQP-001"` in response | `ExpenseCategory` has no `code` field | **Omit from all DTOs** |
| `settleAndTransfer` for REIMBURSE | Architecture doc: REIMBURSE = no wallet movement, only update `AdvanceBalance` | Follow architecture doc — split logic by type |
| `ADVANCE_PAYMENT` in doc comments | `TransactionType.REQUEST_PAYMENT` (line 28) covers all Flow-1 payouts | Use `REQUEST_PAYMENT` for both ADVANCE and EXPENSE |

---

## 2. Architecture — Disburse Logic by Request Type

```
ADVANCE  → settleAndTransfer(PROJECT → USER, REQUEST_PAYMENT)
           + create AdvanceBalance (status = OUTSTANDING)
           + update PhaseCategoryBudget.currentSpent += approvedAmount
           + set request.status = PAID, paidAt

EXPENSE  → settleAndTransfer(PROJECT → USER, REQUEST_PAYMENT)
           + update PhaseCategoryBudget.currentSpent += approvedAmount
           + set request.status = PAID, paidAt

REIMBURSE → NO wallet movement
            + update linked AdvanceBalance.settledAmount += approvedAmount
              (if fully settled → AdvanceBalance.status = SETTLED)
            + update PhaseCategoryBudget.currentSpent += approvedAmount
            + set request.status = PAID, paidAt
```

**Reject (any type):**
```
unlockFunds(PROJECT, projectId, approvedAmount)   ← release reservation
set request.status = REJECTED, rejectReason
add RequestHistory(REJECT, REJECTED)
```

---

## 3. Files to Create

### 3.1 `modules/request/dto/request/DisburseRequest.java`
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DisburseRequest {

    @NotBlank
    private String pin;

    private String note;
}
```

### 3.2 `modules/request/dto/response/AccountantDisbursementSummaryResponse.java`
```java
@Getter
@Builder
public class AccountantDisbursementSummaryResponse {

    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private BigDecimal approvedAmount;
    private String description;
    private RequesterBankSnippet requester;
    private ProjectSnippet project;
    private PhaseSnippet phase;
    private Long categoryId;
    private String categoryName;
    private List<AttachmentResponse> attachments;
    private LocalDateTime createdAt;

    @Getter @Builder
    public static class RequesterBankSnippet {
        private Long id;
        private String fullName;
        private String avatar;
        private String employeeCode;
        private String jobTitle;
        private String departmentName;
        private String bankName;
        private String bankAccountNum;      // unmasked — full account number
        private String bankAccountOwner;
    }

    @Getter @Builder
    public static class ProjectSnippet {
        private Long id;
        private String projectCode;
        private String name;
    }

    @Getter @Builder
    public static class PhaseSnippet {
        private Long id;
        private String phaseCode;
        private String name;
    }
}
```

### 3.3 `modules/request/dto/response/AccountantDisbursementDetailResponse.java`
Same structure as Summary but adds:
```java
private String rejectReason;
private LocalDateTime paidAt;
// phase extended:
//   budgetLimit, currentSpent
private List<RequestHistoryResponse> timeline;
private LocalDateTime updatedAt;
```
`PhaseDetail` inner class:
```java
@Getter @Builder
public static class PhaseDetail {
    private Long id;
    private String phaseCode;
    private String name;
    private BigDecimal budgetLimit;
    private BigDecimal currentSpent;
}
```

### 3.4 `modules/request/dto/response/DisburseResponse.java`
```java
@Getter
@Builder
public class DisburseResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private String transactionCode;  // nullable for REIMBURSE (no wallet txn)
    private BigDecimal amount;
    private LocalDateTime disbursedAt;
}
```

### 3.5 `modules/request/dto/response/AccountantRejectResponse.java`
```java
@Getter
@Builder
public class AccountantRejectResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private String rejectReason;
}
```

### 3.6 `modules/request/controller/AccountantDisbursementController.java`
```java
@RestController
@RequestMapping("/accountant/disbursements")
@RequiredArgsConstructor
@Tag(name = "Accountant - Disbursements", description = "Flow 1 execution: ADVANCE/EXPENSE/REIMBURSE payout")
@SecurityRequirement(name = "bearerAuth")
public class AccountantDisbursementController {

    private final RequestService requestService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccountantDisbursementSummaryResponse>>> list(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { ... }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountantDisbursementDetailResponse>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) { ... }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<ApiResponse<DisburseResponse>> disburse(
            @PathVariable Long id,
            @Valid @RequestBody DisburseRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) { ... }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AccountantRejectResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) { ... }
}
```
> `@AuthenticationPrincipal` is used only to resolve `accountantId`; authorization is enforced at
> service layer via `@PreAuthorize("hasAuthority('REQUEST_DISBURSE')")`.

---

## 4. Files to Modify

### 4.1 `RequestService.java` — add 4 method signatures

```java
PageResponse<AccountantDisbursementSummaryResponse> getAccountantDisbursements(
        RequestType type, String search, int page, int size);

AccountantDisbursementDetailResponse getAccountantDisbursementDetail(Long id);

DisburseResponse disburse(Long id, Long accountantId, DisburseRequest req);

AccountantRejectResponse accountantReject(Long id, Long accountantId, RejectRequestRequest req);
```

### 4.2 `RequestServiceImpl.java`

#### A. Fix `approveTlRequest` — add `lockFunds`

After `requestRepository.save(request)` (line ~355), add:

```java
Long projectId = request.getProject().getId();
walletService.lockFunds(WalletOwnerType.PROJECT, projectId, effectiveAmount);
```

`walletService` is already injected (added in the previous session for Manager flow).

**Why:** `settleAndTransfer` calls `wallet.settle(amount)` which reduces `lockedBalance`. Without a
prior `lockFunds`, `lockedBalance` would go negative.

#### B. Add 4 new service methods

**`getAccountantDisbursements`:**
```java
@Override
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('REQUEST_DISBURSE')")
public PageResponse<AccountantDisbursementSummaryResponse> getAccountantDisbursements(
        RequestType type, String search, int page, int size) {

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Specification<Request> spec = RequestSpecification.filterForAccountantDisbursements(type, search);

    Page<AccountantDisbursementSummaryResponse> result = requestRepository
            .findAll(spec, pageable)
            .map(requestMapper::toAccountantDisbursementSummaryResponse);

    return PageResponse.<AccountantDisbursementSummaryResponse>builder()
            .items(result.getContent())
            .total(result.getTotalElements())
            .page(safePage)
            .size(safeSize)
            .totalPages(result.getTotalPages())
            .build();
}
```

**`getAccountantDisbursementDetail`:**
```java
@Override
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('REQUEST_DISBURSE')")
public AccountantDisbursementDetailResponse getAccountantDisbursementDetail(Long id) {
    Request request = requestRepository.findDetailByIdForAccountant(id)
            .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

    List<RequestHistoryResponse> timeline = requestRepository.findHistoriesByRequestId(id)
            .stream()
            .map(requestMapper::toHistoryResponse)
            .toList();

    return requestMapper.toAccountantDisbursementDetailResponse(request, timeline);
}
```

**`disburse`:**
```java
@Override
@Transactional
@PreAuthorize("hasAuthority('REQUEST_DISBURSE')")
public DisburseResponse disburse(Long id, Long accountantId, DisburseRequest req) {
    // 1. PIN verification
    profileService.verifyMyPin(accountantId, new VerifyMyPinRequest(req.getPin()));

    // 2. Load request
    Request request = requestRepository.findDetailByIdForAccountant(id)
            .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

    if (request.getStatus() != RequestStatus.APPROVED_BY_TEAM_LEADER) {
        throw new BadRequestException("Only APPROVED_BY_TEAM_LEADER requests can be disbursed");
    }

    Long projectId = request.getProject().getId();
    Long requesterId = request.getRequester().getId();
    BigDecimal amount = request.getApprovedAmount();
    User actor = userService.getUserById(accountantId);

    String description = (req.getNote() != null && !req.getNote().isBlank())
            ? req.getNote()
            : request.getType() + " payout — " + request.getRequestCode();

    String transactionCode = null;

    if (request.getType() == RequestType.ADVANCE || request.getType() == RequestType.EXPENSE) {
        // Wallet movement: PROJECT → USER
        Transaction txn = walletService.settleAndTransfer(
                WalletOwnerType.PROJECT, projectId,
                WalletOwnerType.USER, requesterId,
                amount,
                TransactionType.REQUEST_PAYMENT,
                ReferenceType.REQUEST, request.getId(),
                description
        );
        transactionCode = txn.getTransactionCode();

        // Create AdvanceBalance for ADVANCE (tracks the outstanding advance)
        if (request.getType() == RequestType.ADVANCE) {
            AdvanceBalance advanceBalance = AdvanceBalance.builder()
                    .request(request)
                    .user(request.getRequester())
                    .project(request.getProject())
                    .originalAmount(amount)
                    .settledAmount(BigDecimal.ZERO)
                    .status(AdvanceBalanceStatus.OUTSTANDING)
                    .build();
            advanceBalanceRepository.save(advanceBalance);
        }

    } else if (request.getType() == RequestType.REIMBURSE) {
        // No wallet movement — settle the linked AdvanceBalance
        AdvanceBalance advanceBalance = request.getAdvanceBalance();
        if (advanceBalance == null) {
            throw new BadRequestException("REIMBURSE request must have a linked advance balance");
        }
        BigDecimal newSettled = advanceBalance.getSettledAmount().add(amount);
        advanceBalance.setSettledAmount(newSettled);
        if (newSettled.compareTo(advanceBalance.getOriginalAmount()) >= 0) {
            advanceBalance.setStatus(AdvanceBalanceStatus.SETTLED);
        }
        advanceBalanceRepository.save(advanceBalance);
    }

    // Update phase-category budget spend
    if (request.getPhase() != null && request.getCategory() != null) {
        categoryBudgetService.incrementSpent(
                request.getPhase().getId(),
                request.getCategory().getId(),
                amount
        );
    }

    // Mark request as PAID
    request.setStatus(RequestStatus.PAID);
    request.setPaidAt(LocalDateTime.now());
    request.getHistories().add(RequestHistory.builder()
            .request(request)
            .actor(actor)
            .action(RequestAction.PAYOUT)
            .statusAfterAction(RequestStatus.PAID)
            .comment(req.getNote())
            .build());

    requestRepository.save(request);
    return requestMapper.toDisburseResponse(request, transactionCode);
}
```

**`accountantReject`:**
```java
@Override
@Transactional
@PreAuthorize("hasAuthority('REQUEST_DISBURSE')")
public AccountantRejectResponse accountantReject(Long id, Long accountantId, RejectRequestRequest req) {
    Request request = requestRepository.findDetailByIdForAccountant(id)
            .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

    if (request.getStatus() != RequestStatus.APPROVED_BY_TEAM_LEADER) {
        throw new BadRequestException("Only APPROVED_BY_TEAM_LEADER requests can be rejected at this stage");
    }

    // Release the locked funds in the project wallet
    Long projectId = request.getProject().getId();
    walletService.unlockFunds(WalletOwnerType.PROJECT, projectId, request.getApprovedAmount());

    User actor = userService.getUserById(accountantId);
    request.setStatus(RequestStatus.REJECTED);
    request.setRejectReason(req.getReason());
    request.getHistories().add(RequestHistory.builder()
            .request(request)
            .actor(actor)
            .action(RequestAction.REJECT)
            .statusAfterAction(RequestStatus.REJECTED)
            .comment(req.getReason())
            .build());

    requestRepository.save(request);
    return requestMapper.toAccountantRejectResponse(request);
}
```

> **New injections needed in `RequestServiceImpl`:**
> - `private final CategoryBudgetService categoryBudgetService;` (cross-domain — inject interface)
> - `private final AdvanceBalanceRepository advanceBalanceRepository;` (same domain — already imported)
> - `profileService` may need injection if not already present — check existing fields

### 4.3 `RequestMapper.java` — add 4 mapper methods

**`toAccountantDisbursementSummaryResponse(Request request)`:**
- Map `requester` → `RequesterBankSnippet` via `user.getProfile()` for bank info
  - `bankName`, `bankAccountNum`, `bankAccountOwner` from `UserProfile` (unmasked)
  - `departmentName` from `user.getDepartment().getName()`
- Map `attachments` via existing `toAttachmentResponse`

**`toAccountantDisbursementDetailResponse(Request request, List<RequestHistoryResponse> timeline)`:**
- Extends summary mapping
- `phase` → `PhaseDetail` with `budgetLimit`, `currentSpent` from `ProjectPhase`

**`toDisburseResponse(Request request, String transactionCode)`:**
- `id`, `requestCode`, `status`, `transactionCode`, `amount = request.getApprovedAmount()`, `disbursedAt = request.getPaidAt()`

**`toAccountantRejectResponse(Request request)`:**
- `id`, `requestCode`, `status`, `rejectReason`

### 4.4 `RequestRepository.java` — add `findDetailByIdForAccountant`

```java
@Query("""
        SELECT DISTINCT r FROM Request r
        LEFT JOIN FETCH r.requester u
        LEFT JOIN FETCH u.profile up
        LEFT JOIN FETCH up.avatarFile
        LEFT JOIN FETCH u.department
        LEFT JOIN FETCH r.project
        LEFT JOIN FETCH r.phase
        LEFT JOIN FETCH r.category
        LEFT JOIN FETCH r.advanceBalance
        LEFT JOIN FETCH r.attachments att
        LEFT JOIN FETCH att.file
        WHERE r.id = :id
          AND r.status = 'APPROVED_BY_TEAM_LEADER'
          AND r.type IN ('ADVANCE', 'EXPENSE', 'REIMBURSE')
        """)
Optional<Request> findDetailByIdForAccountant(@Param("id") Long id);
```

> Scope guard (`status = APPROVED_BY_TEAM_LEADER`) prevents accountant from operating on wrong-state
> requests. Returns `empty` → service throws `ResourceNotFoundException`.

### 4.5 `RequestSpecification.java` — add `filterForAccountantDisbursements`

```java
public static Specification<Request> filterForAccountantDisbursements(RequestType type, String search) {
    List<RequestType> allowedTypes = (type != null)
            ? List.of(type)
            : List.of(RequestType.ADVANCE, RequestType.EXPENSE, RequestType.REIMBURSE);

    return Specification.where(hasStatus(RequestStatus.APPROVED_BY_TEAM_LEADER))
            .and(hasTypeIn(allowedTypes))
            .and(matchesSearch(search));
}
```

### 4.6 `CategoryBudgetService.java` — add `incrementSpent`

```java
/**
 * Increment currentSpent for a phase-category budget entry when a request is paid.
 * No-op if no budget record exists for the given phase + category combination.
 */
void incrementSpent(Long phaseId, Long categoryId, BigDecimal amount);
```

### 4.7 `CategoryBudgetServiceImpl.java` — implement `incrementSpent`

```java
@Override
@Transactional
public void incrementSpent(Long phaseId, Long categoryId, BigDecimal amount) {
    PhaseCategoryBudgetId budgetId = new PhaseCategoryBudgetId(phaseId, categoryId);
    phaseCategoryBudgetRepository.findById(budgetId).ifPresent(budget -> {
        budget.setCurrentSpent(budget.getCurrentSpent().add(amount));
        phaseCategoryBudgetRepository.save(budget);
    });
}
```

---

## 5. Permission Required

Permission name: **`REQUEST_DISBURSE`**

Must be seeded for `ACCOUNTANT` role in Flyway migration or `DataInitializer`.
Check `docs/rbac-model.md` for the seeding pattern.

If the permission doesn't exist yet in the `permissions` table, create a new migration:
```sql
-- V{N}__Add_request_disburse_permission.sql
INSERT INTO permissions (name, description)
VALUES ('REQUEST_DISBURSE', 'Execute payment for approved ADVANCE/EXPENSE/REIMBURSE requests')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ACCOUNTANT' AND p.name = 'REQUEST_DISBURSE'
ON CONFLICT DO NOTHING;
```

---

## 6. Implementation Order

Follow this order to avoid compilation failures:

1. **`DisburseRequest.java`** — new DTO, no deps
2. **`AccountantDisbursementSummaryResponse.java`** — new DTO
3. **`AccountantDisbursementDetailResponse.java`** — new DTO
4. **`DisburseResponse.java`** — new DTO
5. **`AccountantRejectResponse.java`** — new DTO
6. **`RequestSpecification.java`** — add `filterForAccountantDisbursements` (no new deps)
7. **`RequestRepository.java`** — add `findDetailByIdForAccountant` (no new deps)
8. **`CategoryBudgetService.java`** — add `incrementSpent` method signature
9. **`CategoryBudgetServiceImpl.java`** — implement `incrementSpent`
10. **`RequestMapper.java`** — add 4 mapper methods (depends on new DTOs)
11. **`RequestService.java`** — add 4 method signatures (depends on new DTOs)
12. **`RequestServiceImpl.java`** — fix `approveTlRequest` + implement 4 methods (depends on all above)
13. **`AccountantDisbursementController.java`** — new controller (depends on service interface)
14. **Flyway migration** — seed `REQUEST_DISBURSE` permission

---

## 7. Edge Cases to Handle

| Case | Handling |
|---|---|
| PIN locked | `profileService.verifyMyPin` throws `423 Locked` — propagate as-is |
| PIN incorrect | `verifyMyPin` increments `retryCount`, throws `401` — propagate |
| REIMBURSE without `advanceBalance` | `BadRequestException("REIMBURSE request must have a linked advance balance")` |
| Project wallet insufficient locked balance | `WalletService.settle()` throws — rolls back entire transaction |
| No `PhaseCategoryBudget` record for the phase+category | `incrementSpent` no-ops silently (budget record may not be set up) |
| Concurrent disburse of same request | DB-level: `@Version` on `Request` → optimistic lock → `409 Conflict` |
| ADVANCE approvedAmount < amount (partial approval) | `lockFunds` was called with `effectiveAmount`; `settleAndTransfer` must use `request.getApprovedAmount()` |

---

## 8. SSE Realtime Push

After `settleAndTransfer`, `WalletServiceImpl` automatically calls:
- `pushWalletUpdate(PROJECT, projectId)` → project wallet subscribers (Team Leader via `/users/project/{id}/stream`)
- `pushWalletUpdate(USER, requesterId)` → requester via `/users/stream`
- `pushTransactionHistoryUpdate(...)` for both wallets

No additional SSE calls needed in `RequestServiceImpl` for wallet events.

For notification to requester (optional follow-up): publish `NotificationEvent` via RabbitMQ
(same pattern as other request notifications) with `type = REQUEST_APPROVED` and a "giải ngân thành công" message.
