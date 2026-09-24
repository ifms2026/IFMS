# Request Module — Implementation Plan

> **Scope:** `modules/request` — Employee endpoints only (GET/POST/PUT/DELETE)
> **Cập nhật:** 2026-04-22

---

## 1. Tổng quan

### Hiện trạng

Đã có:
- `entity/` — Request, RequestHistory, RequestAttachment, AdvanceBalance, all enums
- `repository/AdvanceBalanceRepository.java`

Chưa có:
- `repository/RequestRepository.java`
- `dto/request/` — CreateRequestRequest, UpdateRequestRequest, AttachmentRequest
- `dto/response/` — RequestSummaryResponse, RequestDetailResponse, RequestStatusSummaryResponse, AttachmentResponse, RequestHistoryResponse
- `mapper/RequestMapper.java`
- `service/RequestService.java`
- `service/RequestServiceImpl.java`
- `controller/RequestController.java`

### Flyway Migration

**Không cần migration mới.** Các bảng `requests`, `request_histories`, `request_attachments`, `advance_balances` đã có trong V1 + V9 + V10. Schema khớp với entity hiện tại.

---

## 2. Files Cần Tạo

### 2.1 Repository — `RequestRepository.java`

```java
@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {

    // Filtered list — dùng cho GET /requests
    // :statuses là List<RequestStatus>; truyền Arrays.asList(RequestStatus.values()) khi không lọc
    @Query("""
        SELECT r FROM Request r
        LEFT JOIN FETCH r.project
        LEFT JOIN FETCH r.phase
        LEFT JOIN FETCH r.category
        WHERE r.requester.id = :userId
          AND (:type IS NULL OR r.type = :type)
          AND r.status IN :statuses
          AND (:search IS NULL OR :search = '' 
               OR LOWER(r.requestCode) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY r.createdAt DESC
        """)
    Page<Request> findByRequesterWithFilters(
        @Param("userId") Long userId,
        @Param("type") RequestType type,
        @Param("statuses") Collection<RequestStatus> statuses,
        @Param("search") String search,
        Pageable pageable);

    // Summary counts — dùng cho GET /requests/summary
    @Query("""
        SELECT r.status, COUNT(r) FROM Request r
        WHERE r.requester.id = :userId
        GROUP BY r.status
        """)
    List<Object[]> countByStatusForUser(@Param("userId") Long userId);

    // Detail với full join — dùng cho GET /requests/:id
    @Query("""
        SELECT r FROM Request r
        LEFT JOIN FETCH r.project
        LEFT JOIN FETCH r.phase
        LEFT JOIN FETCH r.category
        LEFT JOIN FETCH r.requester
        LEFT JOIN FETCH r.attachments att
        LEFT JOIN FETCH att.file
        WHERE r.id = :id AND r.requester.id = :userId
        """)
    Optional<Request> findDetailByIdAndRequesterId(
        @Param("id") Long id,
        @Param("userId") Long userId);

    // Histories — load riêng tránh cartesian product khi join cùng attachments
    @Query("""
        SELECT h FROM RequestHistory h
        LEFT JOIN FETCH h.actor
        WHERE h.request.id = :requestId
        ORDER BY h.createdAt ASC
        """)
    List<RequestHistory> findHistoriesByRequestId(@Param("requestId") Long requestId);

    // Ownership check — dùng cho PUT và DELETE
    Optional<Request> findByIdAndRequesterId(Long id, Long requesterId);
}
```

---

### 2.2 DTOs

#### `dto/request/AttachmentRequest.java`
```java
@Getter @Setter
public class AttachmentRequest {
    @NotBlank String fileName;
    @NotBlank String cloudinaryPublicId;
    @NotBlank String url;
    String fileType;
    Long size;
}
```

#### `dto/request/CreateRequestRequest.java`
```java
@Getter @Setter
public class CreateRequestRequest {
    @NotNull RequestType type;
    Long projectId;
    Long phaseId;
    Long categoryId;
    Long advanceBalanceId;
    @NotNull @Positive BigDecimal amount;
    @NotBlank String description;
    @Builder.Default List<@Valid AttachmentRequest> attachments = new ArrayList<>();
}
```

#### `dto/request/UpdateRequestRequest.java`
```java
@Getter @Setter
public class UpdateRequestRequest {
    @Positive BigDecimal amount;
    String description;
    List<@Valid AttachmentRequest> attachments; // null = giữ nguyên; [] hoặc non-null = ghi đè toàn bộ
}
```

#### `dto/response/AttachmentResponse.java`
```java
@Getter @Builder
public class AttachmentResponse {
    Long fileId;
    String fileName;
    String cloudinaryPublicId;
    String url;       // permanent URL từ FileStorage.url (không ký lại, không có TTL)
    String fileType;
    Long size;
}
```

#### `dto/response/RequestHistoryResponse.java`
```java
@Getter @Builder
public class RequestHistoryResponse {
    Long id;
    String action;            // RequestAction.name()
    String statusAfterAction; // RequestStatus.name()
    Long actorId;
    String actorName;
    String comment;
    LocalDateTime createdAt;
}
```

#### `dto/response/RequestSummaryResponse.java`
```java
@Getter @Builder
public class RequestSummaryResponse {
    Long id;
    String requestCode;
    String type;
    String status;
    BigDecimal amount;
    BigDecimal approvedAmount;
    String description;
    String rejectReason;
    Long projectId;
    String projectName;
    Long phaseId;
    String phaseName;
    Long categoryId;
    String categoryName;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

#### `dto/response/RequestDetailResponse.java`
```java
@Getter @Builder
public class RequestDetailResponse {
    Long id;
    String requestCode;
    String type;
    String status;          // API-facing: PENDING_APPROVAL | PENDING_ACCOUNTANT | APPROVED | PAID | REJECTED | CANCELLED
    BigDecimal amount;
    BigDecimal approvedAmount;
    String description;
    String rejectReason;
    LocalDateTime paidAt;   // null until PAID
    Long projectId;
    String projectCode;
    String projectName;
    Long phaseId;
    String phaseCode;
    String phaseName;
    Long categoryId;
    String categoryName;
    Long advanceBalanceId;  // non-null for REIMBURSE only
    Long requesterId;
    String requesterName;
    List<AttachmentResponse> attachments;
    List<RequestHistoryResponse> timeline;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

#### `dto/response/RequestStatusSummaryResponse.java`
```java
@Getter @Builder
public class RequestStatusSummaryResponse {
    long totalPendingApproval;    // PENDING
    long totalPendingAccountant;  // APPROVED_BY_TEAM_LEADER + PENDING_ACCOUNTANT_EXECUTION
    long totalApproved;           // APPROVED_BY_MANAGER + APPROVED_BY_CFO
    long totalRejected;           // REJECTED
    long totalPaid;               // PAID
    long totalCancelled;          // CANCELLED
}
```

---

### 2.3 Mapper — `mapper/RequestMapper.java`

```java
@Component
public class RequestMapper {

    public RequestSummaryResponse toSummaryResponse(Request r) { ... }

    public RequestDetailResponse toDetailResponse(Request r, List<RequestHistoryResponse> timeline) { ... }

    public RequestHistoryResponse toHistoryResponse(RequestHistory h) { ... }

    // AttachmentResponse: url lấy trực tiếp từ att.getFile().getUrl() — permanent URL, không ký lại
    public AttachmentResponse toAttachmentResponse(RequestAttachment att) { ... }

    /**
     * Maps internal RequestStatus → API-facing status string (API_Spec.md lines 1014-1341).
     *
     * Internal enum is granular; API exposes simplified 6-value set:
     *   PENDING                                         → "PENDING_APPROVAL"
     *   APPROVED_BY_TEAM_LEADER, PENDING_ACCOUNTANT_EXECUTION → "PENDING_ACCOUNTANT"
     *   APPROVED_BY_MANAGER, APPROVED_BY_CFO            → "APPROVED"
     *   PAID                                            → "PAID"
     *   REJECTED                                        → "REJECTED"
     *   CANCELLED                                       → "CANCELLED"
     *
     * Used in: status field of RequestSummaryResponse / RequestDetailResponse,
     *          and statusAfterAction field of RequestHistoryResponse.
     */
    public String toApiStatus(RequestStatus status) {
        return switch (status) {
            case PENDING                                        -> "PENDING_APPROVAL";
            case APPROVED_BY_TEAM_LEADER, PENDING_ACCOUNTANT_EXECUTION -> "PENDING_ACCOUNTANT";
            case APPROVED_BY_MANAGER, APPROVED_BY_CFO          -> "APPROVED";
            case PAID                                          -> "PAID";
            case REJECTED                                      -> "REJECTED";
            case CANCELLED                                     -> "CANCELLED";
        };
    }
}
```

> **Lưu ý:**
> - `status` và `statusAfterAction` trong mọi response đều phải dùng `toApiStatus()`, KHÔNG dùng `r.getStatus().name()` trực tiếp.
> - `toAttachmentResponse` dùng `att.getFile().getUrl()` trực tiếp — URL đã permanent, KHÔNG gọi Cloudinary để ký lại. `CloudinaryService` không có và không được thêm `generateSignedUrl`.

---

### 2.4 Service Interface — `service/RequestService.java`

```java
public interface RequestService {
    // status: API-facing string (PENDING_APPROVAL | PENDING_ACCOUNTANT | APPROVED | PAID | REJECTED | CANCELLED | null)
    PageResponse<RequestSummaryResponse> getMyRequests(
        Long userId, RequestType type, String status, String search, int page, int limit);

    RequestStatusSummaryResponse getMyRequestSummary(Long userId);

    RequestDetailResponse getRequestDetail(Long id, Long userId);

    RequestDetailResponse createRequest(CreateRequestRequest req, Long userId);

    RequestDetailResponse updateRequest(Long id, UpdateRequestRequest req, Long userId);

    void cancelRequest(Long id, Long userId);
}
```

---

### 2.5 Service Impl — `service/RequestServiceImpl.java`

#### Dependencies (inject)
```java
private final RequestRepository requestRepository;
private final AdvanceBalanceRepository advanceBalanceRepository;
private final FileStorageService fileStorageService;      // cross-domain: file module
private final UserService userService;                   // cross-domain: user module
private final BusinessCodeGenerator codeGenerator;       // common utility
private final RequestMapper requestMapper;
```

#### Private helper — `resolveStatuses`
```java
private Collection<RequestStatus> resolveStatuses(String apiStatus) {
    if (apiStatus == null) return Arrays.asList(RequestStatus.values());
    return switch (apiStatus) {
        case "PENDING_APPROVAL"   -> List.of(RequestStatus.PENDING);
        case "PENDING_ACCOUNTANT" -> List.of(RequestStatus.APPROVED_BY_TEAM_LEADER,
                                             RequestStatus.PENDING_ACCOUNTANT_EXECUTION);
        case "APPROVED"           -> List.of(RequestStatus.APPROVED_BY_MANAGER,
                                             RequestStatus.APPROVED_BY_CFO);
        case "PAID"               -> List.of(RequestStatus.PAID);
        case "REJECTED"           -> List.of(RequestStatus.REJECTED);
        case "CANCELLED"          -> List.of(RequestStatus.CANCELLED);
        default -> throw new BadRequestException("Invalid status filter: " + apiStatus);
    };
}
```

**Về project/phase/category:** `ProjectQueryService` hiện chỉ trả DTO, không trả entity.  
→ Cần thêm 3 method vào `ProjectQueryService` interface:

```java
// Thêm vào ProjectQueryService.java
Project getProjectEntityById(Long projectId);
ProjectPhase getPhaseEntityById(Long phaseId);
ExpenseCategory getCategoryEntityById(Long categoryId);
```

Implement trong `ProjectQueryServiceImpl.java` — query repository, throw `ResourceNotFoundException` nếu không tồn tại.

#### Logic từng method

**`getMyRequests`**
```
1. Tính pageable: PageRequest.of(page - 1, limit) — page 1-based từ client
2. Resolve status filter:
   Collection<RequestStatus> statuses = resolveStatuses(status)
   // resolveStatuses: maps API-facing string → internal enum list
   //   null              → Arrays.asList(RequestStatus.values())  (no filter)
   //   "PENDING_APPROVAL"    → [PENDING]
   //   "PENDING_ACCOUNTANT"  → [APPROVED_BY_TEAM_LEADER, PENDING_ACCOUNTANT_EXECUTION]
   //   "APPROVED"            → [APPROVED_BY_MANAGER, APPROVED_BY_CFO]
   //   "PAID"                → [PAID]
   //   "REJECTED"            → [REJECTED]
   //   "CANCELLED"           → [CANCELLED]
   //   unknown               → throw BadRequestException("Invalid status filter: " + status)
3. requestRepository.findByRequesterWithFilters(userId, type, statuses, search, pageable)
4. Map Page<Request> → PageResponse<RequestSummaryResponse>
5. Return result
```
`@PreAuthorize("hasAuthority('REQUEST_VIEW')")`

---

**`getMyRequestSummary`**
```
1. requestRepository.countByStatusForUser(userId) → List<Object[]> (status, count)
2. Build Map<RequestStatus, Long> từ kết quả
3. Tổng hợp:
   - totalPendingApproval    = map.getOrDefault(PENDING, 0)
   - totalPendingAccountant  = map.getOrDefault(APPROVED_BY_TEAM_LEADER, 0)
                             + map.getOrDefault(PENDING_ACCOUNTANT_EXECUTION, 0)
   - totalApproved           = map.getOrDefault(APPROVED_BY_MANAGER, 0)
                             + map.getOrDefault(APPROVED_BY_CFO, 0)
   - totalPaid               = map.getOrDefault(PAID, 0)
   - totalRejected           = map.getOrDefault(REJECTED, 0)
   - totalCancelled          = map.getOrDefault(CANCELLED, 0)
4. Return RequestStatusSummaryResponse
```
`@PreAuthorize("hasAuthority('REQUEST_VIEW')")`

---

**`getRequestDetail`**
```
1. requestRepository.findDetailByIdAndRequesterId(id, userId)
   → throw ResourceNotFoundException("Request not found") nếu empty
   (attachments + att.file đã được JOIN FETCH — không cần gọi FileStorageService riêng)
2. Load histories riêng: requestRepository.findHistoriesByRequestId(id)
3. Map sang RequestDetailResponse:
   - attachments: requestMapper.toAttachmentResponse(att) → dùng att.getFile().getUrl() trực tiếp
   - timeline: sorted ASC đã đảm bảo trong query
4. Return result
```
`@PreAuthorize("hasAuthority('REQUEST_VIEW')")`

---

**`createRequest`**
```
Bước 1 — Validate type-specific fields:
  | Field             | ADVANCE | EXPENSE | REIMBURSE | PROJECT_TOPUP | DEPT_TOPUP |
  |-------------------|---------|---------|-----------|---------------|------------|
  | projectId         | ✓ req   | ✓ req   | ✓ req     | ✓ req         | null       |
  | phaseId           | ✓ req   | ✓ req   | ✓ req     | null          | null       |
  | categoryId        | ✓ req   | ✓ req   | ✓ req     | null          | null       |
  | advanceBalanceId  | null    | null    | ✓ req     | null          | null       |
  | attachments       | optional| ✓ req≥1 | ✓ req≥1  | []            | []         |

  Throw BadRequestException("projectId is required for type {type}") nếu vi phạm.

Bước 2 — Load entities (chỉ khi required):
  User requester     = userService.getUserById(userId)
  Project project    = (projectId != null) ? projectQueryService.getProjectEntityById(projectId) : null
  ProjectPhase phase = (phaseId != null)   ? projectQueryService.getPhaseEntityById(phaseId)     : null
  ExpenseCategory cat= (categoryId != null) ? projectQueryService.getCategoryEntityById(categoryId) : null

Bước 3 — REIMBURSE only:
  AdvanceBalance ab = advanceBalanceRepository.findById(advanceBalanceId)
    .orElseThrow(() -> new ResourceNotFoundException("AdvanceBalance not found"))
  if (!ab.getUser().getId().equals(userId))
    throw new BadRequestException("Advance balance does not belong to you")
  if (ab.isSettled())
    throw new AdvanceBalanceAlreadySettledException(advanceBalanceId)

Bước 4 — Generate requestCode:
  String deptCode = requester.getDepartment().getCode()  // dept code như "IT", "FIN"
  String code = codeGenerator.generate(BusinessCodeType.REQUEST, deptCode)
  // → "REQ-IT-0426-001"

Bước 5 — Save attachments (if any):
  List<FileStorage> files = req.getAttachments().stream()
    .map(att -> fileStorageService.save(toFileStorageRequest(att)))
    .toList()

Bước 6 — Build và save Request:
  Request entity = Request.builder()
    .requestCode(code)
    .requester(requester)
    .project(project)
    .phase(phase)
    .category(cat)
    .advanceBalance(ab)   // null nếu không phải REIMBURSE
    .type(req.getType())
    .amount(req.getAmount())
    .description(req.getDescription())
    .status(RequestStatus.PENDING)
    .build()
  files.forEach(entity::addAttachment)
  requestRepository.save(entity)

Bước 7 — Return RequestDetailResponse (timeline: [])
```
`@PreAuthorize("hasAuthority('REQUEST_CREATE')")`

---

**`updateRequest`**
```
1. requestRepository.findByIdAndRequesterId(id, userId)
   → throw ResourceNotFoundException nếu không tìm thấy

2. Verify request.getStatus() == PENDING
   → throw BadRequestException("Request cannot be modified after approval")

3. Update allowed fields:
   if (req.getAmount() != null)      request.setAmount(req.getAmount())
   if (req.getDescription() != null) request.setDescription(req.getDescription())

4. Replace attachments nếu req.getAttachments() != null:
   a. Xóa tất cả RequestAttachment hiện tại (orphanRemoval = true xử lý tự động)
      request.getAttachments().clear()
   b. Save FileStorage mới và addAttachment cho từng cái

5. requestRepository.save(request)
6. Return getRequestDetail(id, userId) để lấy full detail với signed URLs
```
`@PreAuthorize("hasAuthority('REQUEST_CREATE')")`

---

**`cancelRequest`**
```
1. requestRepository.findByIdAndRequesterId(id, userId)
   → throw ResourceNotFoundException nếu không tìm thấy

2. if (!request.isCancellable())  // status != PENDING
   throw new BadRequestException("Request can only be cancelled when PENDING")

3. request.setStatus(RequestStatus.CANCELLED)

4. Tạo RequestHistory:
   User actor = userService.getUserById(userId)
   RequestHistory history = RequestHistory.builder()
     .request(request)
     .actor(actor)
     .action(RequestAction.CANCEL)
     .statusAfterAction(RequestStatus.CANCELLED)
     .comment(null)
     .build()
   request.getHistories().add(history)

5. requestRepository.save(request)
   // cascade ALL trên histories → history tự lưu cùng request
```
`@PreAuthorize("hasAuthority('REQUEST_CREATE')")`

---

### 2.6 Controller — `controller/RequestController.java`

```java
@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
@Tag(name = "Request", description = "Employee request management")
@SecurityRequirement(name = "bearerAuth")
public class RequestController {

    private final RequestService requestService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RequestSummaryResponse>>> getMyRequests(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) String status,  // API-facing: PENDING_APPROVAL | PENDING_ACCOUNTANT | APPROVED | PAID | REJECTED | CANCELLED
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
            requestService.getMyRequests(principal.getUser().getId(), type, status, search, page, limit)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<RequestStatusSummaryResponse>> getMyRequestSummary(
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
            requestService.getMyRequestSummary(principal.getUser().getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> getRequestDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
            requestService.getRequestDetail(id, principal.getUser().getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RequestDetailResponse>> createRequest(
            @Valid @RequestBody CreateRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            requestService.createRequest(req, principal.getUser().getId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> updateRequest(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
            requestService.updateRequest(id, req, principal.getUser().getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        requestService.cancelRequest(id, principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Request cancelled successfully")));
    }
}
```

---

## 3. Cross-Domain Changes Cần Thực Hiện

### 3.1 Thêm vào `ProjectQueryService.java`
```java
// Internal entity-returning methods — dùng bởi cross-domain services (RequestServiceImpl)
Project getProjectEntityById(Long projectId);
ProjectPhase getPhaseEntityById(Long phaseId);
ExpenseCategory getCategoryEntityById(Long categoryId);
```

Implement trong `ProjectQueryServiceImpl.java`:
```java
@Override
public Project getProjectEntityById(Long projectId) {
    return projectRepository.findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
}
// tương tự cho phase và category
```

### 3.2 Cloudinary URL — không cần ký lại
`CloudinaryService` **không có và không được thêm** `generateSignedUrl`.  
URL attachment là permanent (`FileStorage.url`), lấy trực tiếp qua JOIN FETCH trong `findDetailByIdAndRequesterId`.  
Xem thêm: `docs/file-storage.md` §6.

---

## 4. Thứ Tự Implement

1. `RequestRepository.java` — tất cả queries
2. DTOs (request + response)
3. `ProjectQueryService` — thêm 3 entity methods + implement trong Impl
4. `RequestMapper.java`
5. `RequestService.java` + `RequestServiceImpl.java`
6. `RequestController.java`
7. Security config — đảm bảo `/requests/**` không bị whitelist anonymous

---

## 5. Edge Cases & Validation Notes

| Case | Xử lý |
|------|--------|
| REIMBURSE với advanceBalanceId của người khác | `BadRequestException` |
| REIMBURSE với balance đã SETTLED | `AdvanceBalanceAlreadySettledException` |
| EXPENSE không có attachment | `BadRequestException("Attachments required for EXPENSE")` |
| Update request không phải PENDING | `BadRequestException("Cannot modify after approval")` |
| Cancel request không phải PENDING | `BadRequestException("Can only cancel PENDING request")` |
| GET /requests/:id của người khác | `ResourceNotFoundException` (không lộ existence) |
| projectId không tồn tại | `ResourceNotFoundException` từ `getProjectEntityById` |
| `?status=` với giá trị không hợp lệ | `BadRequestException("Invalid status filter: ...")` từ `resolveStatuses()` |

---

## 6. Security Config

Đảm bảo `/api/v1/requests/**` được bảo vệ bởi JWT filter (không nằm trong whitelist).  
Kiểm tra `SecurityConfig.java` — thêm nếu cần.
