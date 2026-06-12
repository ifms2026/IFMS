package com.mkwang.backend.modules.organization.mapper;

import com.mkwang.backend.modules.organization.dto.response.DepartmentDetailResponse;
import com.mkwang.backend.modules.organization.dto.response.DepartmentSummaryResponse;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DepartmentMapper {

    public DepartmentSummaryResponse toSummary(Department dept, long employeeCount) {
        return DepartmentSummaryResponse.builder()
                .id(dept.getId())
                .name(dept.getName())
                .code(dept.getCode())
                .manager(buildSummaryManagerRef(dept.getManager()))
                .employeeCount(employeeCount)
                .totalProjectQuota(dept.getTotalProjectQuota())
                .totalAvailableBalance(dept.getTotalAvailableBalance())
                .createdAt(dept.getCreatedAt())
                .build();
    }

    public DepartmentDetailResponse toDetail(Department dept, List<User> members) {
        List<DepartmentDetailResponse.MemberResponse> memberResponses = members.stream()
                .map(this::toMemberResponse)
                .toList();

        return DepartmentDetailResponse.builder()
                .id(dept.getId())
                .name(dept.getName())
                .code(dept.getCode())
                .manager(buildDetailManagerRef(dept.getManager()))
                .totalProjectQuota(dept.getTotalProjectQuota())
                .totalAvailableBalance(dept.getTotalAvailableBalance())
                .members(memberResponses)
                .createdAt(dept.getCreatedAt())
                .updatedAt(dept.getUpdatedAt())
                .build();
    }

    private DepartmentSummaryResponse.ManagerRef buildSummaryManagerRef(User manager) {
        if (manager == null) return null;
        return DepartmentSummaryResponse.ManagerRef.builder()
                .id(manager.getId())
                .fullName(manager.getFullName())
                .build();
    }

    private DepartmentDetailResponse.ManagerRef buildDetailManagerRef(User manager) {
        if (manager == null) return null;
        return DepartmentDetailResponse.ManagerRef.builder()
                .id(manager.getId())
                .fullName(manager.getFullName())
                .build();
    }

    private DepartmentDetailResponse.MemberResponse toMemberResponse(User user) {
        UserProfile profile = user.getProfile();
        return DepartmentDetailResponse.MemberResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                .email(user.getEmail())
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .avatar(profile != null && profile.getAvatarFile() != null ? profile.getAvatarFile().getUrl() : null)
                .status(user.getStatus().name())
                .build();
    }
}
