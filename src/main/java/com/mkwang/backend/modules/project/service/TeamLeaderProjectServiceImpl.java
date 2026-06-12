package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.project.dto.request.AddProjectMemberRequest;
import com.mkwang.backend.modules.project.dto.request.CreatePhaseRequest;
import com.mkwang.backend.modules.project.dto.request.UpdatePhaseRequest;
import com.mkwang.backend.modules.project.dto.request.UpdateProjectMemberRequest;
import com.mkwang.backend.modules.project.dto.response.AvailableMemberResponse;
import com.mkwang.backend.modules.project.dto.response.MemberProjectInfoResponse;
import com.mkwang.backend.modules.project.dto.response.MemberRecentRequestResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectMemberResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectPhaseResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectSummaryResponse;
import com.mkwang.backend.modules.project.dto.response.TeamMemberDetailResponse;
import com.mkwang.backend.modules.project.dto.response.TeamMemberSummaryResponse;
import com.mkwang.backend.modules.project.entity.PhaseStatus;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectMember;
import com.mkwang.backend.modules.project.entity.ProjectMemberId;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.project.entity.ProjectRole;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.repository.ProjectMemberRepository;
import com.mkwang.backend.modules.project.repository.ProjectMemberSpecification;
import com.mkwang.backend.modules.project.repository.ProjectPhaseRepository;
import com.mkwang.backend.modules.project.repository.ProjectRepository;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.request.repository.RequestRepository;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.repository.UserRepository;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TeamLeaderProjectServiceImpl implements TeamLeaderProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final RequestRepository requestRepository;
    private final WalletRepository walletRepository;

    // ─────────────────────────────────────────────────────────────────
    // GET /team-leader/projects
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_VIEW_ACTIVE')")
    public PageResponse<ProjectSummaryResponse> getLeaderProjects(
            User currentUser, ProjectStatus status, String search, int page, int limit) {

        String searchLike = (search == null || search.isBlank()) ? "%" : "%" + search.toLowerCase() + "%";
        List<Project> allProjects = projectRepository.findLeaderProjects(
                currentUser.getId(), status, searchLike);

        int total = allProjects.size();
        int fromIndex = Math.max(0, (page - 1) * limit);
        int toIndex = Math.min(fromIndex + limit, total);

        List<ProjectSummaryResponse> items = allProjects.subList(fromIndex, toIndex).stream()
                .map(p -> {
                    int memberCount = projectRepository.countMembersByProjectId(p.getId());
                    String currentPhaseName = p.getCurrentPhase() != null ? p.getCurrentPhase().getName() : null;
                    Long currentPhaseId = p.getCurrentPhase() != null ? p.getCurrentPhase().getId() : null;
                    return new ProjectSummaryResponse(
                            p.getId(),
                            p.getProjectCode(),
                            p.getName(),
                            p.getStatus().name(),
                            p.getTotalBudget(),
                            p.getAvailableBudget(),
                            p.getTotalSpent(),
                            memberCount,
                            currentPhaseId,
                            currentPhaseName,
                            p.getCreatedAt()
                    );
                })
                .toList();

        int totalPages = (int) Math.ceil((double) total / limit);
        return PageResponse.<ProjectSummaryResponse>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(limit)
                .totalPages(totalPages)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /team-leader/projects/:id
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_VIEW_ACTIVE')")
    public ProjectDetailResponse getLeaderProjectDetail(User currentUser, Long projectId) {
        Project project = getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        List<PhaseDetailResponse> phases = projectPhaseRepository
                .findByProject_IdOrderByCreatedAtAsc(projectId).stream()
                .map(ph -> new PhaseDetailResponse(
                        ph.getId(),
                        ph.getPhaseCode(),
                        ph.getName(),
                        ph.getBudgetLimit(),
                        ph.getCurrentSpent(),
                        ph.getStatus().name(),
                        ph.getStartDate(),
                        ph.getEndDate()
                ))
                .toList();

        List<ProjectMemberResponse> members = projectMemberRepository
                .findMembersWithProfileByProjectId(projectId).stream()
                .map(pm -> toMemberResponse(pm))
                .toList();

        Long currentPhaseId = project.getCurrentPhase() != null ? project.getCurrentPhase().getId() : null;

        return new ProjectDetailResponse(
                project.getId(),
                project.getProjectCode(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getTotalBudget(),
                project.getAvailableBudget(),
                project.getTotalSpent(),
                project.getDepartment() != null ? project.getDepartment().getId() : null,
                project.getManager() != null ? project.getManager().getId() : null,
                currentPhaseId,
                phases,
                members,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /team-leader/projects/:id/members
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_MEMBER_MANAGE')")
    public ProjectMemberResponse addMember(User currentUser, Long projectId, AddProjectMemberRequest request) {
        Project project = getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        User newUser = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        // Must be ACTIVE
        if (!newUser.isActive()) {
            throw new BadRequestException("User is not active");
        }

        // Must belong to same department
        Long projectDeptId = project.getDepartment() != null ? project.getDepartment().getId() : null;
        Long userDeptId = newUser.getDepartment() != null ? newUser.getDepartment().getId() : null;
        if (projectDeptId == null || !projectDeptId.equals(userDeptId)) {
            throw new BadRequestException("User must belong to the same department as the project");
        }

        // Must not already be a member
        if (projectMemberRepository.existsByProject_IdAndUser_Id(projectId, request.userId())) {
            throw new BadRequestException("User is already a member of this project");
        }

        ProjectMember member = ProjectMember.builder()
                .id(new ProjectMemberId(projectId, request.userId()))
                .project(project)
                .user(newUser)
                .projectRole(ProjectRole.MEMBER)
                .position(request.position())
                .build();

        projectMemberRepository.save(member);
        return toMemberResponse(member);
    }

    // ─────────────────────────────────────────────────────────────────
    // PUT /team-leader/projects/:id/members/:userId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_MEMBER_MANAGE')")
    public ProjectMemberResponse updateMember(User currentUser, Long projectId, Long userId, UpdateProjectMemberRequest request) {
        getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        ProjectMember member = projectMemberRepository.findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", "userId", userId));

        member.setPosition(request.position());
        projectMemberRepository.save(member);
        return toMemberResponse(member);
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE /team-leader/projects/:id/members/:userId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_MEMBER_MANAGE')")
    public void removeMember(User currentUser, Long projectId, Long userId) {
        getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        // Cannot remove yourself (the leader)
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Team Leader cannot remove themselves from the project");
        }

        ProjectMember member = projectMemberRepository.findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", "userId", userId));

        // Cannot remove if member has pending requests in this project
        List<Long> singleProjectList = List.of(projectId);
        if (projectMemberRepository.hasPendingRequestsInProjects(userId, singleProjectList)) {
            throw new BadRequestException("Cannot remove member with pending requests in this project");
        }

        projectMemberRepository.delete(member);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /team-leader/projects/:id/available-members
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_MEMBER_MANAGE')")
    public List<AvailableMemberResponse> getAvailableMembers(User currentUser, Long projectId, String search) {
        Project project = getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        Long departmentId = project.getDepartment() != null ? project.getDepartment().getId() : null;
        if (departmentId == null) {
            throw new BadRequestException("Project has no department assigned");
        }

        String searchLike = (search == null || search.isBlank()) ? "%" : "%" + search.toLowerCase() + "%";

        return projectMemberRepository.findAvailableUsersForProject(departmentId, projectId, searchLike)
                .stream()
                .map(user -> {
                    UserProfile profile = user.getProfile();
                    String avatar = (profile != null && profile.getAvatarFile() != null)
                            ? profile.getAvatarFile().getUrl() : null;
                    String employeeCode = profile != null ? profile.getEmployeeCode() : null;
                    String jobTitle = profile != null ? profile.getJobTitle() : null;
                    return new AvailableMemberResponse(
                            user.getId(),
                            user.getFullName(),
                            employeeCode,
                            avatar,
                            user.getEmail(),
                            jobTitle
                    );
                })
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /team-leader/projects/:id/phases
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_PHASE_MANAGE')")
    public ProjectPhaseResponse createPhase(User currentUser, Long projectId, CreatePhaseRequest request) {
        Project project = getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        // Validate: sum of existing phase budgets + new budget <= project.availableBudget
        BigDecimal existingPhaseBudgetTotal = projectPhaseRepository
                .findByProject_IdOrderByCreatedAtAsc(projectId).stream()
                .map(ProjectPhase::getBudgetLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = existingPhaseBudgetTotal.add(request.budgetLimit());
        if (newTotal.compareTo(project.getAvailableBudget()) > 0) {
            throw new BadRequestException(
                    "Total phase budget (" + newTotal + ") would exceed project available budget ("
                            + project.getAvailableBudget() + ")"
            );
        }

        String phaseCode = businessCodeGenerator.generate(BusinessCodeType.PHASE, request.name());

        ProjectPhase phase = ProjectPhase.builder()
                .phaseCode(phaseCode)
                .project(project)
                .name(request.name())
                .budgetLimit(request.budgetLimit())
                .currentSpent(BigDecimal.ZERO)
                .status(PhaseStatus.ACTIVE)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        ProjectPhase saved = projectPhaseRepository.save(phase);
        return toPhaseResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    // PUT /team-leader/projects/:id/phases/:phaseId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_PHASE_MANAGE')")
    public ProjectPhaseResponse updatePhase(User currentUser, Long projectId, Long phaseId, UpdatePhaseRequest request) {
        Project project = getProjectOrThrow(projectId);
        assertLeaderOf(currentUser.getId(), projectId);

        ProjectPhase phase = projectPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectPhase", "id", phaseId));

        if (!phase.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Phase does not belong to this project");
        }

        if (request.name() != null && !request.name().isBlank()) {
            phase.setName(request.name());
        }

        if (request.budgetLimit() != null) {
            // Validate budget change
            if (request.budgetLimit().compareTo(phase.getCurrentSpent()) < 0) {
                throw new BadRequestException(
                        "New budgetLimit (" + request.budgetLimit() + ") cannot be less than currentSpent ("
                                + phase.getCurrentSpent() + ")"
                );
            }

            // If increasing, check against project available_budget
            if (request.budgetLimit().compareTo(phase.getBudgetLimit()) > 0) {
                BigDecimal increase = request.budgetLimit().subtract(phase.getBudgetLimit());
                if (increase.compareTo(project.getAvailableBudget()) > 0) {
                    throw new BadRequestException(
                            "Insufficient project available budget (" + project.getAvailableBudget()
                                    + ") to increase phase budget by " + increase
                    );
                }
            }

            phase.setBudgetLimit(request.budgetLimit());
        }

        if (request.endDate() != null) {
            phase.setEndDate(request.endDate());
        }

        if (request.status() != null) {
            phase.setStatus(request.status());
        }

        ProjectPhase saved = projectPhaseRepository.save(phase);
        return toPhaseResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /team-leader/team-members
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_VIEW_ACTIVE')")
    public PageResponse<TeamMemberSummaryResponse> getTeamMembers(
            User currentUser, Long projectId, String search, int page, int limit) {

        // 1. Determine TL's project scope
        List<Long> leaderProjectIds = projectMemberRepository.findProjectIdsByLeader(currentUser.getId());
        if (leaderProjectIds.isEmpty()) {
            return PageResponse.<TeamMemberSummaryResponse>builder()
                    .items(List.of()).total(0).page(page).size(limit).totalPages(0).build();
        }

        // 2. Validate optional projectId filter — must be within TL scope
        if (projectId != null && !leaderProjectIds.contains(projectId)) {
            return PageResponse.<TeamMemberSummaryResponse>builder()
                    .items(List.of()).total(0).page(page).size(limit).totalPages(0).build();
        }

        // 3. Build Specification — DB-level filtering per CLAUDE.md convention
        Specification<ProjectMember> spec = ProjectMemberSpecification.filter(
                leaderProjectIds, projectId, search, currentUser.getId());

        // 4. Fetch matching members from DB (no pagination yet — dedup required first)
        //    We use findAll(spec) without pageable because deduplication by userId
        //    across multiple projects must happen before slicing.
        List<ProjectMember> matchedMembers = projectMemberRepository.findAll(spec);

        // 5. Deduplicate by userId, keeping all their project memberships
        Map<Long, List<ProjectMember>> byUser = new LinkedHashMap<>();
        for (ProjectMember pm : matchedMembers) {
            byUser.computeIfAbsent(pm.getUser().getId(), k -> new ArrayList<>()).add(pm);
        }

        // 6. Apply in-memory pagination on the deduplicated user list
        List<Long> allUserIds = new ArrayList<>(byUser.keySet());
        int total = allUserIds.size();
        int fromIndex = Math.max(0, (page - 1) * limit);
        int toIndex = Math.min(fromIndex + limit, total);
        List<Long> pageUserIds = allUserIds.subList(fromIndex, toIndex);

        // 7. Determine scope for pending-count query (narrow if projectId filter applied)
        List<Long> pendingScope = (projectId != null) ? List.of(projectId) : leaderProjectIds;

        // 8. Enrich each user: load profile data (already in session from spec query),
        //    fetch wallet lockedBalance, and count pending requests
        List<TeamMemberSummaryResponse> items = pageUserIds.stream()
                .map(uid -> {
                    List<ProjectMember> memberships = byUser.get(uid);
                    // Profile is already loaded via the spec join (or lazy within same transaction)
                    User u = memberships.get(0).getUser();
                    UserProfile up = u.getProfile();

                    BigDecimal debtBalance = walletRepository
                            .findByOwnerTypeAndOwnerId(WalletOwnerType.USER, uid)
                            .map(w -> w.getLockedBalance())
                            .orElse(BigDecimal.ZERO);

                    int pendingCount = requestRepository
                            .countPendingForMemberInProjects(uid, pendingScope);

                    List<MemberProjectInfoResponse> projects = memberships.stream()
                            .map(pm -> new MemberProjectInfoResponse(
                                    pm.getProject().getId(),
                                    pm.getProject().getProjectCode(),
                                    pm.getProject().getName(),
                                    pm.getPosition(),
                                    null  // omitted in list view
                            ))
                            .toList();

                    return new TeamMemberSummaryResponse(
                            u.getId(),
                            u.getFullName(),
                            u.getEmail(),
                            up != null ? up.getEmployeeCode() : null,
                            (up != null && up.getAvatarFile() != null) ? up.getAvatarFile().getUrl() : null,
                            up != null ? up.getJobTitle() : null,
                            u.getStatus().name(),
                            debtBalance,
                            pendingCount,
                            projects
                    );
                })
                .toList();

        int totalPages = (int) Math.ceil((double) total / limit);
        return PageResponse.<TeamMemberSummaryResponse>builder()
                .items(items).total(total).page(page).size(limit).totalPages(totalPages).build();
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /team-leader/team-members/:userId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_VIEW_ACTIVE')")
    public TeamMemberDetailResponse getTeamMemberDetail(User currentUser, Long userId) {
        List<Long> leaderProjectIds = projectMemberRepository.findProjectIdsByLeader(currentUser.getId());
        if (leaderProjectIds.isEmpty()) {
            throw new ResourceNotFoundException("TeamMember", "userId", userId);
        }

        // Verify this user is actually a member of at least one of TL's projects
        List<Long> memberProjectIds = projectMemberRepository.findMemberProjectIds(userId, leaderProjectIds);
        if (memberProjectIds.isEmpty()) {
            throw new ResourceNotFoundException("TeamMember", "userId", userId);
        }

        User member = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        UserProfile profile = member.getProfile();

        BigDecimal debtBalance = walletRepository
                .findByOwnerTypeAndOwnerId(WalletOwnerType.USER, userId)
                .map(w -> w.getLockedBalance())
                .orElse(BigDecimal.ZERO);

        int pendingCount = requestRepository.countPendingForMemberInProjects(userId, leaderProjectIds);

        // Build projects across ALL leader's projects for this member (with joinedAt)
        List<MemberProjectInfoResponse> allProjects = new ArrayList<>();
        for (Long pid : memberProjectIds) {
            projectMemberRepository.findWithProjectByProjectIdAndUserId(pid, userId).ifPresent(pm ->
                    allProjects.add(new MemberProjectInfoResponse(
                            pm.getProject().getId(),
                            pm.getProject().getProjectCode(),
                            pm.getProject().getName(),
                            pm.getPosition(),
                            pm.getJoinedAt()
                    ))
            );
        }

        // Recent requests: top 10
        List<Request> recentReqs = requestRepository.findTop10RecentByRequesterInProjects(
                userId, leaderProjectIds, PageRequest.of(0, 10));

        List<MemberRecentRequestResponse> recentRequests = recentReqs.stream()
                .map(r -> new MemberRecentRequestResponse(
                        r.getId(),
                        r.getRequestCode(),
                        r.getType().name(),
                        r.getAmount(),
                        r.getStatus().name(),
                        r.getProject() != null ? r.getProject().getProjectCode() : null,
                        r.getCategory() != null ? r.getCategory().getName() : null,
                        r.getCreatedAt()
                ))
                .toList();

        return new TeamMemberDetailResponse(
                member.getId(),
                member.getFullName(),
                member.getEmail(),
                profile != null ? profile.getEmployeeCode() : null,
                (profile != null && profile.getAvatarFile() != null) ? profile.getAvatarFile().getUrl() : null,
                profile != null ? profile.getJobTitle() : null,
                profile != null ? profile.getPhoneNumber() : null,
                member.getStatus().name(),
                debtBalance,
                pendingCount,
                allProjects,
                recentRequests
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private void assertLeaderOf(Long userId, Long projectId) {
        if (!projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRole(projectId, userId, ProjectRole.LEADER)) {
            throw new UnauthorizedException("You are not the Team Leader of this project");
        }
    }

    private ProjectMemberResponse toMemberResponse(ProjectMember pm) {
        User user = pm.getUser();
        UserProfile profile = user != null ? user.getProfile() : null;
        String avatar = (profile != null && profile.getAvatarFile() != null)
                ? profile.getAvatarFile().getUrl() : null;
        String employeeCode = profile != null ? profile.getEmployeeCode() : null;
        return new ProjectMemberResponse(
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : null,
                avatar,
                employeeCode,
                pm.getProjectRole() != null ? pm.getProjectRole().name() : null,
                pm.getPosition(),
                pm.getJoinedAt()
        );
    }

    private ProjectPhaseResponse toPhaseResponse(ProjectPhase phase) {
        return new ProjectPhaseResponse(
                phase.getId(),
                phase.getPhaseCode(),
                phase.getName(),
                phase.getBudgetLimit(),
                phase.getCurrentSpent(),
                phase.getStatus().name(),
                phase.getStartDate(),
                phase.getEndDate()
        );
    }
}
