package com.mkwang.backend.modules.project.mapper;

import com.mkwang.backend.modules.profile.entity.UserProfile;
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
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ManagerProjectMapper {

    public DepartmentMemberSummaryResponse toDepartmentMemberSummaryResponse(
            User user,
            int pendingRequestsCount,
            BigDecimal debtBalance) {

        UserProfile profile = user.getProfile();
        return new DepartmentMemberSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                profile != null ? profile.getEmployeeCode() : null,
                resolveAvatar(profile),
                profile != null ? profile.getJobTitle() : null,
                user.getStatus().name(),
                pendingRequestsCount,
                debtBalance
        );
    }

    public DepartmentMemberDetailResponse toDepartmentMemberDetailResponse(
            User user,
            int pendingRequestsCount,
            BigDecimal debtBalance,
            List<DepartmentMemberProjectAssignmentResponse> assignedProjects) {

        UserProfile profile = user.getProfile();
        return new DepartmentMemberDetailResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                profile != null ? profile.getEmployeeCode() : null,
                resolveAvatar(profile),
                profile != null ? profile.getJobTitle() : null,
                profile != null ? profile.getPhoneNumber() : null,
                user.getStatus().name(),
                debtBalance,
                pendingRequestsCount,
                assignedProjects
        );
    }

    public DepartmentMemberProjectAssignmentResponse toDepartmentMemberProjectAssignmentResponse(ProjectMember projectMember) {
        return new DepartmentMemberProjectAssignmentResponse(
                projectMember.getProject().getId(),
                projectMember.getProject().getProjectCode(),
                projectMember.getProject().getName(),
                projectMember.getProjectRole().name(),
                projectMember.getPosition()
        );
    }

    public ProjectSummaryResponse toProjectSummaryResponse(Project project, int memberCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getProjectCode(),
                project.getName(),
                project.getStatus().name(),
                project.getTotalBudget(),
                project.getAvailableBudget(),
                project.getTotalSpent(),
                memberCount,
                project.getCurrentPhase() != null ? project.getCurrentPhase().getId() : null,
                project.getCurrentPhase() != null ? project.getCurrentPhase().getName() : null,
                project.getCreatedAt()
        );
    }

    public ProjectDetailResponse toProjectDetailResponse(
            Project project,
            List<PhaseDetailResponse> phases,
            List<ProjectMemberResponse> members) {

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
                project.getCurrentPhase() != null ? project.getCurrentPhase().getId() : null,
                phases,
                members,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    public PhaseDetailResponse toPhaseDetailResponse(ProjectPhase phase) {
        return new PhaseDetailResponse(
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

    public ProjectMemberResponse toProjectMemberResponse(ProjectMember projectMember) {
        User user = projectMember.getUser();
        UserProfile profile = user != null ? user.getProfile() : null;

        return new ProjectMemberResponse(
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : null,
                resolveAvatar(profile),
                profile != null ? profile.getEmployeeCode() : null,
                projectMember.getProjectRole() != null ? projectMember.getProjectRole().name() : null,
                projectMember.getPosition(),
                projectMember.getJoinedAt()
        );
    }

    public AvailableMemberResponse toAvailableMemberResponse(User user) {
        UserProfile profile = user.getProfile();
        return new AvailableMemberResponse(
                user.getId(),
                user.getFullName(),
                profile != null ? profile.getEmployeeCode() : null,
                resolveAvatar(profile),
                user.getEmail(),
                profile != null ? profile.getJobTitle() : null
        );
    }

    private String resolveAvatar(UserProfile profile) {
        return profile != null && profile.getAvatarFile() != null
                ? profile.getAvatarFile().getUrl()
                : null;
    }
}

