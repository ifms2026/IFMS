# Team Leader Approval Flow — Implementation Plan

Endpoints: `GET/POST /team-leader/approvals[/:id[/approve|reject]]`  
Spec reference: `docs/API_Spec.md §3.3` (lines 1746–1898)  
Status alignment: `PENDING → APPROVED_BY_TEAM_LEADER → PAID` (no intermediate state)

---

## Pre-conditions

- `ExpenseCategory` has no `categoryCode` column → omit from all DTOs
- Avatar path: `user.getProfile().getAvatarFile()` (nullable `FileStorage`) → use `.getUrl()` or `null`
- `UserProfile` is lazy-loaded on `User` → must be JOIN FETCH'd in any JPQL that reads avatar/jobTitle
- Permission to use on service methods: `REQUEST_APPROVE_TL` — verify it is seeded in `roles_permissions` before running

---

## Step 1 — Request DTOs

**`modules/request/dto/request/ApproveRequestRequest.java`**
```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRequestRequest {
    private String comment;                           // optional

    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal approvedAmount;                // optional; defaults to request.amount if null
}
```

**`modules/request/dto/request/RejectRequestRequest.java`**
```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequestRequest {
    @NotBlank
    private String reason;
}
```

---

## Step 2 — Response DTOs

**`modules/request/dto/response/TlApprovalSummaryResponse.java`**  
Used by the list endpoint. Nested static classes live inside this file.

```java
@Getter
@Builder
public class TlApprovalSummaryResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private BigDecimal amount;
    private RequesterSnippet requester;
    private ProjectSnippet project;
    private PhaseSnippet phase;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;

    @Getter @Builder public static class RequesterSnippet {
        private Long id;
        private String fullName;
        private String avatar;       // nullable
        private String employeeCode; // nullable
    }

    @Getter @Builder public static class ProjectSnippet {
        private Long id;
        private String projectCode;
    }

    @Getter @Builder public static class PhaseSnippet {
        private Long id;
        private String phaseCode;
    }
}
```

**`modules/request/dto/response/TlApprovalDetailResponse.java`**  
Used by the detail endpoint.

```java
@Getter
@Builder
public class TlApprovalDetailResponse {
    private Long id;
    private String requestCode;
    private RequestType type;
    private RequestStatus status;
    private BigDecimal amount;
    private String description;
    private RequesterDetail requester;
    private ProjectDetail project;
    private PhaseDetail phase;
    private Long categoryId;
    private String categoryName;
    private List<AttachmentResponse> attachments;   // reuse existing DTO
    private LocalDateTime createdAt;

    @Getter @Builder public static class RequesterDetail {
        private Long id;
        private String fullName;
        private String avatar;       // nullable
        private String employeeCode; // nullable
        private String jobTitle;     // nullable
        private String email;
    }

    @Getter @Builder public static class ProjectDetail {
        private Long id;
        private String projectCode;
        private String name;
    }

    @Getter @Builder public static class PhaseDetail {
        private Long id;
        private String phaseCode;
        private String name;
        private BigDecimal budgetLimit;
        private BigDecimal currentSpent;
    }
}
```

**`modules/request/dto/response/TlApproveResponse.java`**
```java
@Getter
@Builder
public class TlApproveResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private BigDecimal approvedAmount;
    private String comment;          // echoes back what was submitted
}
```

**`modules/request/dto/response/TlRejectResponse.java`**
```java
@Getter
@Builder
public class TlRejectResponse {
    private Long id;
    private String requestCode;
    private RequestStatus status;
    private String rejectReason;
}
```

---

## Step 3 — Extend `ProjectQueryService`

Add to **`modules/project/service/ProjectQueryService.java`**:
```java
List<Long> getLeaderProjectIds(Long leaderId);
```

Add to **`ProjectMemberRepository.java`**:
```java
@Query("SELECT pm.project.id FROM ProjectMember pm WHERE pm.user.id = :userId AND pm.projectRole = 'LEADER'")
List<Long> findProjectIdsByLeader(@Param("userId") Long userId);
```

Implement in **`ProjectQueryServiceImpl.java`**:
```java
@Override
@Transactional(readOnly = true)
public List<Long> getLeaderProjectIds(Long leaderId) {
    return projectMemberRepository.findProjectIdsByLeader(leaderId);
}
```

---

## Step 4 — Extend `RequestSpecification`

Add the following to **`modules/request/repository/RequestSpecification.java`**:

```java
public static Specification<Request> hasTypeIn(List<RequestType> types) {
    return (root, query, cb) ->
            (types == null || types.isEmpty()) ? null : root.get("type").in(types);
}

public static Specification<Request> projectIdIn(List<Long> projectIds) {
    return (root, query, cb) ->
            (projectIds == null || projectIds.isEmpty())
                    ? cb.disjunction()     // empty list → match nothing
                    : root.get("project").get("id").in(projectIds);
}

public static Specification<Request> hasProjectId(Long projectId) {
    return (root, query, cb) ->
            projectId == null ? null : cb.equal(root.get("project").get("id"), projectId);
}

/**
 * Combiner for GET /team-leader/approvals.
 * Always restricts to: status=PENDING, type in [ADVANCE,EXPENSE,REIMBURSE],
 * project within the leader's scope. Optional type/projectId/search narrow further.
 */
public static Specification<Request> filterForTlApprovals(
        List<Long> leaderProjectIds,
        RequestType type,
        Long projectId,
        String search) {

    List<RequestType> allowedTypes = (type != null)
            ? List.of(type)
            : List.of(RequestType.ADVANCE, RequestType.EXPENSE, RequestType.REIMBURSE);

    return Specification.where(hasStatus(RequestStatus.PENDING))
            .and(hasTypeIn(allowedTypes))
            .and(projectIdIn(leaderProjectIds))
            .and(hasProjectId(projectId))
            .and(matchesSearch(search));
}
```

---

## Step 5 — Extend `RequestRepository`

Add to **`modules/request/repository/RequestRepository.java`**:

```java
/**
 * Eagerly fetches all associations needed for TlApprovalDetailResponse.
 * Scoped to projects the caller leads — prevents cross-project data leak.
 */
@Query("""
        SELECT DISTINCT r FROM Request r
        LEFT JOIN FETCH r.requester u
        LEFT JOIN FETCH u.profile up
        LEFT JOIN FETCH up.avatarFile
        LEFT JOIN FETCH r.project
        LEFT JOIN FETCH r.phase
        LEFT JOIN FETCH r.category
        LEFT JOIN FETCH r.attachments att
        LEFT JOIN FETCH att.file
        WHERE r.id = :id AND r.project.id IN :projectIds
        """)
Optional<Request> findDetailByIdForTl(
        @Param("id") Long id,
        @Param("projectIds") List<Long> projectIds);
```

---

## Step 6 — Extend `RequestMapper`

Add to **`modules/request/mapper/RequestMapper.java`**:

```java
public TlApprovalSummaryResponse toTlApprovalSummaryResponse(Request request) {
    User u = request.getRequester();
    UserProfile p = (u != null) ? u.getProfile() : null;

    return TlApprovalSummaryResponse.builder()
            .id(request.getId())
            .requestCode(request.getRequestCode())
            .type(request.getType())
            .amount(request.getAmount())
            .requester(TlApprovalSummaryResponse.RequesterSnippet.builder()
                    .id(u != null ? u.getId() : null)
                    .fullName(u != null ? u.getFullName() : null)
                    .avatar(resolveAvatar(p))
                    .employeeCode(p != null ? p.getEmployeeCode() : null)
                    .build())
            .project(request.getProject() != null
                    ? TlApprovalSummaryResponse.ProjectSnippet.builder()
                            .id(request.getProject().getId())
                            .projectCode(request.getProject().getProjectCode())
                            .build()
                    : null)
            .phase(request.getPhase() != null
                    ? TlApprovalSummaryResponse.PhaseSnippet.builder()
                            .id(request.getPhase().getId())
                            .phaseCode(request.getPhase().getPhaseCode())
                            .build()
                    : null)
            .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
            .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
            .createdAt(request.getCreatedAt())
            .build();
}

public TlApprovalDetailResponse toTlApprovalDetailResponse(Request request) {
    User u = request.getRequester();
    UserProfile p = (u != null) ? u.getProfile() : null;
    ProjectPhase phase = request.getPhase();

    return TlApprovalDetailResponse.builder()
            .id(request.getId())
            .requestCode(request.getRequestCode())
            .type(request.getType())
            .status(request.getStatus())
            .amount(request.getAmount())
            .description(request.getDescription())
            .requester(TlApprovalDetailResponse.RequesterDetail.builder()
                    .id(u != null ? u.getId() : null)
                    .fullName(u != null ? u.getFullName() : null)
                    .email(u != null ? u.getEmail() : null)
                    .avatar(resolveAvatar(p))
                    .employeeCode(p != null ? p.getEmployeeCode() : null)
                    .jobTitle(p != null ? p.getJobTitle() : null)
                    .build())
            .project(request.getProject() != null
                    ? TlApprovalDetailResponse.ProjectDetail.builder()
                            .id(request.getProject().getId())
                            .projectCode(request.getProject().getProjectCode())
                            .name(request.getProject().getName())
                            .build()
                    : null)
            .phase(phase != null
                    ? TlApprovalDetailResponse.PhaseDetail.builder()
                            .id(phase.getId())
                            .phaseCode(phase.getPhaseCode())
                            .name(phase.getName())
                            .budgetLimit(phase.getBudgetLimit())
                            .currentSpent(phase.getCurrentSpent())
                            .build()
                    : null)
            .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
            .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
            .attachments(request.getAttachments().stream().map(this::toAttachmentResponse).toList())
            .createdAt(request.getCreatedAt())
            .build();
}

public TlApproveResponse toTlApproveResponse(Request request, String comment) {
    return TlApproveResponse.builder()
            .id(request.getId())
            .requestCode(request.getRequestCode())
            .status(request.getStatus())
            .approvedAmount(request.getApprovedAmount())
            .comment(comment)
            .build();
}

public TlRejectResponse toTlRejectResponse(Request request) {
    return TlRejectResponse.builder()
            .id(request.getId())
            .requestCode(request.getRequestCode())
            .status(request.getStatus())
            .rejectReason(request.getRejectReason())
            .build();
}

// Private helper — add once, reuse across all TL mappers
private String resolveAvatar(UserProfile profile) {
    if (profile == null || profile.getAvatarFile() == null) return null;
    return profile.getAvatarFile().getUrl();
}
```

> `resolveAvatar` returns the raw stored URL. If Cloudinary signed URLs are needed here, inject `CloudinaryService` (or wherever signing is done) and call it. Check how other mappers (e.g., `UserMapper`) handle avatar signing and follow the same pattern.

---

## Step 7 — Extend `RequestService` interface

Add to **`modules/request/service/RequestService.java`**:

```java
PageResponse<TlApprovalSummaryResponse> getTlApprovals(
        Long leaderId, RequestType type, Long projectId, String search, int page, int size);

TlApprovalDetailResponse getTlApprovalDetail(Long id, Long leaderId);

TlApproveResponse approveTlRequest(Long id, Long leaderId, ApproveRequestRequest req);

TlRejectResponse rejectTlRequest(Long id, Long leaderId, RejectRequestRequest req);
```

---

## Step 8 — Implement in `RequestServiceImpl`

Add to **`modules/request/service/RequestServiceImpl.java`**:

```java
// ── Allowed types for Flow 1 ──────────────────────────────────────────────────
private static final List<RequestType> FLOW1_TYPES =
        List.of(RequestType.ADVANCE, RequestType.EXPENSE, RequestType.REIMBURSE);

@Override
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('REQUEST_APPROVE_TL')")
public PageResponse<TlApprovalSummaryResponse> getTlApprovals(
        Long leaderId, RequestType type, Long projectId, String search, int page, int size) {

    List<Long> leaderProjectIds = projectQueryService.getLeaderProjectIds(leaderId);
    if (leaderProjectIds.isEmpty()) {
        return PageResponse.<TlApprovalSummaryResponse>builder()
                .items(List.of()).total(0L).page(page).size(size).totalPages(0).build();
    }

    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Specification<Request> spec =
            RequestSpecification.filterForTlApprovals(leaderProjectIds, type, projectId, search);

    Page<TlApprovalSummaryResponse> result = requestRepository
            .findAll(spec, pageable)
            .map(requestMapper::toTlApprovalSummaryResponse);

    return PageResponse.<TlApprovalSummaryResponse>builder()
            .items(result.getContent())
            .total(result.getTotalElements())
            .page(page)
            .size(size)
            .totalPages(result.getTotalPages())
            .build();
}

@Override
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('REQUEST_APPROVE_TL')")
public TlApprovalDetailResponse getTlApprovalDetail(Long id, Long leaderId) {
    List<Long> leaderProjectIds = projectQueryService.getLeaderProjectIds(leaderId);
    Request request = requestRepository.findDetailByIdForTl(id, leaderProjectIds)
            .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
    return requestMapper.toTlApprovalDetailResponse(request);
}

@Override
@Transactional
@PreAuthorize("hasAuthority('REQUEST_APPROVE_TL')")
public TlApproveResponse approveTlRequest(Long id, Long leaderId, ApproveRequestRequest req) {
    List<Long> leaderProjectIds = projectQueryService.getLeaderProjectIds(leaderId);
    Request request = requestRepository.findDetailByIdForTl(id, leaderProjectIds)
            .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

    if (request.getStatus() != RequestStatus.PENDING) {
        throw new BadRequestException("Only PENDING requests can be approved");
    }
    if (!FLOW1_TYPES.contains(request.getType())) {
        throw new BadRequestException("Only ADVANCE/EXPENSE/REIMBURSE requests go through Team Leader approval");
    }

    BigDecimal effective = (req.getApprovedAmount() != null) ? req.getApprovedAmount() : request.getAmount();
    if (effective.compareTo(request.getAmount()) > 0) {
        throw new BadRequestException("approvedAmount cannot exceed requested amount");
    }

    request.setStatus(RequestStatus.APPROVED_BY_TEAM_LEADER);
    request.setApprovedAmount(effective);

    User actor = userService.getUserById(leaderId);
    request.getHistories().add(RequestHistory.builder()
            .request(request)
            .actor(actor)
            .action(RequestAction.APPROVE)
            .statusAfterAction(RequestStatus.APPROVED_BY_TEAM_LEADER)
            .comment(req.getComment())
            .build());

    requestRepository.save(request);
    return requestMapper.toTlApproveResponse(request, req.getComment());
}

@Override
@Transactional
@PreAuthorize("hasAuthority('REQUEST_APPROVE_TL')")
public TlRejectResponse rejectTlRequest(Long id, Long leaderId, RejectRequestRequest req) {
    List<Long> leaderProjectIds = projectQueryService.getLeaderProjectIds(leaderId);
    Request request = requestRepository.findDetailByIdForTl(id, leaderProjectIds)
            .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

    if (request.getStatus() != RequestStatus.PENDING) {
        throw new BadRequestException("Only PENDING requests can be rejected");
    }
    if (!FLOW1_TYPES.contains(request.getType())) {
        throw new BadRequestException("Only ADVANCE/EXPENSE/REIMBURSE requests go through Team Leader approval");
    }

    request.setStatus(RequestStatus.REJECTED);
    request.setRejectReason(req.getReason());

    User actor = userService.getUserById(leaderId);
    request.getHistories().add(RequestHistory.builder()
            .request(request)
            .actor(actor)
            .action(RequestAction.REJECT)
            .statusAfterAction(RequestStatus.REJECTED)
            .comment(req.getReason())
            .build());

    requestRepository.save(request);
    return requestMapper.toTlRejectResponse(request);
}
```

> `userService` and `requestMapper` are already injected in `RequestServiceImpl`. No new fields needed.

---

## Step 9 — New Controller

**`modules/request/controller/TeamLeaderApprovalController.java`**

```java
@RestController
@RequestMapping("/team-leader/approvals")
@RequiredArgsConstructor
@Tag(name = "Team Leader – Approvals", description = "Flow 1 approval for ADVANCE/EXPENSE/REIMBURSE")
@SecurityRequirement(name = "bearerAuth")
public class TeamLeaderApprovalController {

    private final RequestService requestService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TlApprovalSummaryResponse>>> getApprovals(
            @AuthenticationPrincipal UserDetailsAdapter principal,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getTlApprovals(
                        principal.getUser().getId(), type, projectId, search, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TlApprovalDetailResponse>> getApprovalDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.getTlApprovalDetail(id, principal.getUser().getId())));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<TlApproveResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.approveTlRequest(id, principal.getUser().getId(), req)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<TlRejectResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestRequest req,
            @AuthenticationPrincipal UserDetailsAdapter principal) {
        return ResponseEntity.ok(ApiResponse.success(
                requestService.rejectTlRequest(id, principal.getUser().getId(), req)));
    }
}
```

---

## Summary

| File | Action |
|---|---|
| `dto/request/ApproveRequestRequest.java` | **Create** |
| `dto/request/RejectRequestRequest.java` | **Create** |
| `dto/response/TlApprovalSummaryResponse.java` | **Create** |
| `dto/response/TlApprovalDetailResponse.java` | **Create** |
| `dto/response/TlApproveResponse.java` | **Create** |
| `dto/response/TlRejectResponse.java` | **Create** |
| `controller/TeamLeaderApprovalController.java` | **Create** |
| `service/RequestService.java` | **Modify** — 4 new method signatures |
| `service/RequestServiceImpl.java` | **Modify** — 4 implementations + `FLOW1_TYPES` constant |
| `repository/RequestSpecification.java` | **Modify** — 3 predicates + `filterForTlApprovals` |
| `repository/RequestRepository.java` | **Modify** — `findDetailByIdForTl` query |
| `mapper/RequestMapper.java` | **Modify** — 4 mapping methods + `resolveAvatar` helper |
| `project/service/ProjectQueryService.java` | **Modify** — `getLeaderProjectIds` |
| `project/service/ProjectQueryServiceImpl.java` | **Modify** — implement `getLeaderProjectIds` |
| `project/repository/ProjectMemberRepository.java` | **Modify** — `findProjectIdsByLeader` query |

**No Flyway migration needed** — no schema changes.  
**No new `RequestStatus` enum value** — `APPROVED_BY_TEAM_LEADER` already exists.
