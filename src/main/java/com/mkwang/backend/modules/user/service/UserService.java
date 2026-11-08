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
import java.util.Optional;

public interface UserService {

    OnboardUserResponse onboardUser(OnboardUserRequest request);

    User getUserById(Long userId);
    User getUserByEmployeeCode(String employeeCode);

    /**
     * Non-throwing lookup for Excel row validators. getUserByEmployeeCode() is
     * @Transactional, so a row-not-found exception from it marks the caller's
     * ambient transaction rollback-only even if the caller catches it, which
     * turns a normal "invalid row" into a 500 UnexpectedRollbackException on
     * commit. Validators must use this method and throw their own BaseException
     * instead of letting getUserByEmployeeCode()'s exception cross a @Transactional boundary.
     */
    Optional<User> findUserByEmployeeCode(String employeeCode);
    List<User> getPayrollReferenceUsers(int max);
    void lockFinancialScope(Long userId);

    Page<User> getDepartmentMembers(Long departmentId, Long excludedUserId, String roleName, String search, Pageable pageable);

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
