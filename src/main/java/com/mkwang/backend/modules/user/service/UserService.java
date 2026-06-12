package com.mkwang.backend.modules.user.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.user.dto.request.AdminCreateUserRequest;
import com.mkwang.backend.modules.user.dto.request.AdminUpdateUserRequest;
import com.mkwang.backend.modules.user.dto.request.OnboardUserRequest;
import com.mkwang.backend.modules.user.dto.response.AdminCreateUserResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserDetailResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserStatusResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserSummaryResponse;
import com.mkwang.backend.modules.user.dto.response.OnboardUserResponse;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {

    OnboardUserResponse onboardUser(OnboardUserRequest request);

    User getUserById(Long userId);

    Page<User> getDepartmentMembers(Long departmentId, Long excludedUserId, String search, Pageable pageable);

    User getDepartmentUserById(Long departmentId, Long userId);

    List<User> getActiveTeamLeadersByDepartmentId(Long departmentId);

    // ── Admin endpoints ───────────────────────────────────────────

    PageResponse<AdminUserSummaryResponse> getAdminUsers(
            String roleName, Long departmentId, String statusStr, String search, int page, int limit);

    AdminUserDetailResponse getAdminUserDetail(Long id);

    AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest request);

    AdminUserDetailResponse adminUpdateUser(Long id, AdminUpdateUserRequest request);

    AdminUserStatusResponse lockUser(Long id);

    AdminUserStatusResponse unlockUser(Long id);

    void resetUserPassword(Long id);

    // ── Cross-domain helpers ──────────────────────────────────────

    long countUsersByDepartmentId(Long departmentId);

    List<User> getUsersByDepartmentIdWithProfile(Long departmentId);

    long countActiveUsers();

    List<User> getActiveUsersByRoleName(String roleName);
}
