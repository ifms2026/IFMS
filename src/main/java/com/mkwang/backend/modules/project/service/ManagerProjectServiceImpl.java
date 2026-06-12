package com.mkwang.backend.modules.project.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.project.dto.request.CreateManagerProjectRequest;
import com.mkwang.backend.modules.project.dto.request.UpdateManagerProjectRequest;
import com.mkwang.backend.modules.project.dto.response.AvailableMemberResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberDetailResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberProjectAssignmentResponse;
import com.mkwang.backend.modules.project.dto.response.DepartmentMemberSummaryResponse;
import com.mkwang.backend.modules.project.dto.response.PhaseDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectDetailResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectMemberResponse;
import com.mkwang.backend.modules.project.dto.response.ProjectSummaryResponse;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectMember;
import com.mkwang.backend.modules.project.entity.ProjectMemberId;
import com.mkwang.backend.modules.project.entity.ProjectRole;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import com.mkwang.backend.modules.project.mapper.ManagerProjectMapper;
import com.mkwang.backend.modules.project.repository.ProjectMemberRepository;
import com.mkwang.backend.modules.project.repository.ProjectPhaseRepository;
import com.mkwang.backend.modules.project.repository.ProjectRepository;
import com.mkwang.backend.modules.project.repository.ProjectSpecification;
import com.mkwang.backend.modules.request.repository.RequestRepository;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.service.UserService;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ManagerProjectServiceImpl implements ManagerProjectService {

    private static final String DEFAULT_LEADER_POSITION = "Team Leader";

    private final UserService userService;
    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final RequestRepository requestRepository;
    private final WalletRepository walletRepository;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final ManagerProjectMapper managerProjectMapper;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_DEPT')")
    public PageResponse<DepartmentMemberSummaryResponse> getDepartmentMembers(
            Long managerId, String search, int page, int limit) {

        User manager = getManagerOrThrow(managerId);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(limit, 1);

        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.ASC, "fullName"));
        Page<User> memberPage = userService.getDepartmentMembers(
                manager.getDepartment().getId(),
                manager.getId(),
                normalizeSearch(search),
                pageable
        );

        List<Long> userIds = memberPage.getContent().stream().map(User::getId).toList();
        Map<Long, BigDecimal> debtBalanceByUserId = loadDebtBalances(userIds);
        Map<Long, Integer> pendingCountByUserId = loadPendingCounts(userIds);

        List<DepartmentMemberSummaryResponse> items = memberPage.getContent().stream()
                .map(user -> managerProjectMapper.toDepartmentMemberSummaryResponse(
                        user,
                        pendingCountByUserId.getOrDefault(user.getId(), 0),
                        debtBalanceByUserId.getOrDefault(user.getId(), BigDecimal.ZERO)))
                .toList();

        return PageResponse.<DepartmentMemberSummaryResponse>builder()
                .items(items)
                .total(memberPage.getTotalElements())
                .page(safePage)
                .size(safeLimit)
                .totalPages(memberPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_DEPT')")
    public DepartmentMemberDetailResponse getDepartmentMemberDetail(Long managerId, Long memberId) {
        User manager = getManagerOrThrow(managerId);
        Long departmentId = manager.getDepartment().getId();

        User member = userService.getDepartmentUserById(departmentId, memberId);
        BigDecimal debtBalance = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.USER, memberId)
                .map(wallet -> wallet.getLockedBalance())
                .orElse(BigDecimal.ZERO);

        int pendingCount = requestRepository.countPendingForRequester(memberId);

        List<DepartmentMemberProjectAssignmentResponse> assignedProjects = projectMemberRepository
                .findByUser_IdAndProject_Department_IdOrderByJoinedAtDesc(memberId, departmentId)
                .stream()
                .map(managerProjectMapper::toDepartmentMemberProjectAssignmentResponse)
                .toList();

        return managerProjectMapper.toDepartmentMemberDetailResponse(member, pendingCount, debtBalance, assignedProjects);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_VIEW_ACTIVE')")
    public PageResponse<ProjectSummaryResponse> getDepartmentProjects(
            Long managerId, ProjectStatus status, String search, int page, int limit) {

        User manager = getManagerOrThrow(managerId);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(limit, 1);

        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Project> spec = ProjectSpecification.filter(
                manager.getDepartment().getId(),
                status,
                normalizeSearch(search)
        );

        Page<Project> projectPage = projectRepository.findAll(spec, pageable);
        Map<Long, Integer> memberCounts = loadMemberCounts(projectPage.getContent().stream().map(Project::getId).toList());

        List<ProjectSummaryResponse> items = projectPage.getContent().stream()
                .map(project -> managerProjectMapper.toProjectSummaryResponse(
                        project,
                        memberCounts.getOrDefault(project.getId(), 0)))
                .toList();

        return PageResponse.<ProjectSummaryResponse>builder()
                .items(items)
                .total(projectPage.getTotalElements())
                .page(safePage)
                .size(safeLimit)
                .totalPages(projectPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROJECT_VIEW_ACTIVE')")
    public ProjectDetailResponse getDepartmentProjectDetail(Long managerId, Long projectId) {
        User manager = getManagerOrThrow(managerId);
        Project project = getDepartmentProjectOrThrow(manager.getDepartment().getId(), projectId);
        return buildProjectDetail(project);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_CREATE')")
    public ProjectDetailResponse createProject(Long managerId, CreateManagerProjectRequest request) {
        User manager = getManagerOrThrow(managerId);
        Long departmentId = manager.getDepartment().getId();

        User teamLeader = validateAssignableLeader(request.teamLeaderId(), departmentId);

        Project project = Project.builder()
                .projectCode(businessCodeGenerator.generate(BusinessCodeType.PROJECT, request.name()))
                .name(request.name())
                .description(request.description())
                .department(manager.getDepartment())
                .manager(manager)
                .status(ProjectStatus.PLANNING)
                .totalBudget(request.totalBudget())
                .availableBudget(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .build();

        Project savedProject = projectRepository.save(project);

        ProjectMember leaderMember = ProjectMember.builder()
                .id(new ProjectMemberId(savedProject.getId(), teamLeader.getId()))
                .project(savedProject)
                .user(teamLeader)
                .projectRole(ProjectRole.LEADER)
                .position(DEFAULT_LEADER_POSITION)
                .build();
        projectMemberRepository.save(leaderMember);

        return buildProjectDetail(savedProject);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    public ProjectDetailResponse updateProject(Long managerId, Long projectId, UpdateManagerProjectRequest request) {
        User manager = getManagerOrThrow(managerId);
        Long departmentId = manager.getDepartment().getId();

        Project project = getDepartmentProjectOrThrow(departmentId, projectId);

        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.totalBudget() != null) {
            validateAndUpdateTotalBudget(project, request.totalBudget());
        }
        if (request.status() != null) {
            validateStatusTransition(project.getStatus(), request.status());
            project.setStatus(request.status());
        }
        if (request.teamLeaderId() != null) {
            updateProjectLeader(project, request.teamLeaderId(), departmentId);
        }

        Project savedProject = projectRepository.save(project);
        return buildProjectDetail(savedProject);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_DEPT')")
    public List<AvailableMemberResponse> getDepartmentTeamLeaders(Long managerId) {
        User manager = getManagerOrThrow(managerId);

        return userService.getActiveTeamLeadersByDepartmentId(manager.getDepartment().getId()).stream()
                .filter(user -> !user.getId().equals(manager.getId()))
                .map(managerProjectMapper::toAvailableMemberResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_DEPT')")
    public BigDecimal[] getDeptBudgetSnapshot(Long managerId) {
        User manager = getManagerOrThrow(managerId);
        Long deptId = manager.getDepartment().getId();
        BigDecimal totalQuota = projectRepository.sumTotalBudgetByDeptId(deptId);
        BigDecimal totalAvailable = projectRepository.sumAvailableBudgetByDeptId(deptId);
        return new BigDecimal[]{totalQuota, totalAvailable};
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REQUEST_VIEW_DEPT')")
    public Map<com.mkwang.backend.modules.project.entity.ProjectStatus, Long> getDeptProjectStatusCounts(Long managerId) {
        User manager = getManagerOrThrow(managerId);
        Long deptId = manager.getDepartment().getId();
        Map<com.mkwang.backend.modules.project.entity.ProjectStatus, Long> counts = new HashMap<>();
        for (Object[] row : projectRepository.countByStatusForDept(deptId)) {
            com.mkwang.backend.modules.project.entity.ProjectStatus status =
                    (com.mkwang.backend.modules.project.entity.ProjectStatus) row[0];
            Long count = ((Number) row[1]).longValue();
            counts.put(status, count);
        }
        return counts;
    }

    private ProjectDetailResponse buildProjectDetail(Project project) {
        List<PhaseDetailResponse> phases = projectPhaseRepository.findByProject_IdOrderByCreatedAtAsc(project.getId())
                .stream()
                .map(managerProjectMapper::toPhaseDetailResponse)
                .toList();

        List<ProjectMemberResponse> members = projectMemberRepository.findMembersWithProfileByProjectId(project.getId())
                .stream()
                .map(managerProjectMapper::toProjectMemberResponse)
                .toList();

        return managerProjectMapper.toProjectDetailResponse(project, phases, members);
    }

    private User getManagerOrThrow(Long managerId) {
        User manager = userService.getUserById(managerId);

        if (manager.getDepartment() == null || manager.getDepartment().getId() == null) {
            throw new BadRequestException("Manager must belong to a department");
        }

        if (manager.getRole() == null || manager.getRole().getName() == null
                || !"MANAGER".equalsIgnoreCase(manager.getRole().getName())) {
            throw new BadRequestException("Current user is not a manager");
        }

        return manager;
    }

    private Project getDepartmentProjectOrThrow(Long departmentId, Long projectId) {
        return projectRepository.findByIdAndDepartment_Id(projectId, departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private User validateAssignableLeader(Long userId, Long departmentId) {
        User user = userService.getUserById(userId);

        if (!user.isActive()) {
            throw new BadRequestException("Team leader must be ACTIVE");
        }

        if (user.getDepartment() == null || !departmentId.equals(user.getDepartment().getId())) {
            throw new BadRequestException("Team leader must belong to the manager's department");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : null;
        if (roleName == null || (!"TEAM_LEADER".equalsIgnoreCase(roleName) && !"EMPLOYEE".equalsIgnoreCase(roleName))) {
            throw new BadRequestException("teamLeaderId must have TEAM_LEADER or EMPLOYEE role");
        }

        return user;
    }

    private void validateAndUpdateTotalBudget(Project project, BigDecimal newBudget) {
        if (newBudget.compareTo(project.getTotalSpent()) < 0) {
            throw new BadRequestException("totalBudget cannot be lower than totalSpent");
        }

        if (newBudget.compareTo(project.getTotalBudget()) < 0) {
            throw new BadRequestException("totalBudget can only be increased");
        }

        project.setTotalBudget(newBudget);
    }

    private void validateStatusTransition(ProjectStatus currentStatus, ProjectStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }

        boolean allowed = switch (currentStatus) {
            case PLANNING -> targetStatus == ProjectStatus.ACTIVE;
            case ACTIVE -> targetStatus == ProjectStatus.PAUSED || targetStatus == ProjectStatus.CLOSED;
            case PAUSED -> targetStatus == ProjectStatus.ACTIVE;
            case CLOSED -> false;
        };

        if (!allowed) {
            throw new BadRequestException("Invalid project status transition: " + currentStatus + " -> " + targetStatus);
        }
    }

    private void updateProjectLeader(Project project, Long newLeaderId, Long departmentId) {
        User newLeader = validateAssignableLeader(newLeaderId, departmentId);
        List<ProjectMember> currentLeaders = projectMemberRepository.findByProject_IdAndProjectRole(project.getId(), ProjectRole.LEADER);

        boolean alreadyLeader = currentLeaders.stream().anyMatch(pm -> pm.getUser().getId().equals(newLeaderId));
        if (alreadyLeader && currentLeaders.size() == 1) {
            return;
        }

        for (ProjectMember leader : currentLeaders) {
            if (!leader.getUser().getId().equals(newLeaderId)) {
                leader.setProjectRole(ProjectRole.MEMBER);
            }
        }

        Optional<ProjectMember> newLeaderMembership = projectMemberRepository.findByProject_IdAndUser_Id(project.getId(), newLeaderId);
        if (newLeaderMembership.isPresent()) {
            ProjectMember member = newLeaderMembership.get();
            member.setProjectRole(ProjectRole.LEADER);
            if (member.getPosition() == null || member.getPosition().isBlank()) {
                member.setPosition(DEFAULT_LEADER_POSITION);
            }
        } else {
            ProjectMember leaderMember = ProjectMember.builder()
                    .id(new ProjectMemberId(project.getId(), newLeaderId))
                    .project(project)
                    .user(newLeader)
                    .projectRole(ProjectRole.LEADER)
                    .position(DEFAULT_LEADER_POSITION)
                    .build();
            projectMemberRepository.save(leaderMember);
        }

        projectMemberRepository.saveAll(currentLeaders);
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    private Map<Long, BigDecimal> loadDebtBalances(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : walletRepository.findLockedBalancesForUsers(userIds)) {
            Long userId = ((Number) row[0]).longValue();
            BigDecimal balance = row[1] instanceof BigDecimal ? (BigDecimal) row[1] : BigDecimal.ZERO;
            result.put(userId, balance);
        }
        return result;
    }

    private Map<Long, Integer> loadPendingCounts(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : requestRepository.countPendingByRequesterIds(userIds)) {
            Long userId = ((Number) row[0]).longValue();
            Integer count = ((Number) row[1]).intValue();
            result.put(userId, count);
        }
        return result;
    }

    private Map<Long, Integer> loadMemberCounts(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : projectMemberRepository.countMembersByProjectIds(projectIds)) {
            Long projectId = ((Number) row[0]).longValue();
            Integer count = ((Number) row[1]).intValue();
            result.put(projectId, count);
        }
        return result;
    }
}

