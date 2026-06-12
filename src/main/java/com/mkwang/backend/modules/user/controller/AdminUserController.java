package com.mkwang.backend.modules.user.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.user.dto.request.AdminCreateUserRequest;
import com.mkwang.backend.modules.user.dto.request.AdminUpdateUserRequest;
import com.mkwang.backend.modules.user.dto.response.AdminCreateUserResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserDetailResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserStatusResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserSummaryResponse;
import com.mkwang.backend.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin — Users", description = "Admin user management APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all users with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserSummaryResponse>>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        PageResponse<AdminUserSummaryResponse> result =
                userService.getAdminUsers(role, departmentId, status, search, page, limit);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full user detail by ID")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable Long id) {
        AdminUserDetailResponse result = userService.getAdminUserDetail(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "Create a new user account, auto-generate credentials and send email")
    public ResponseEntity<ApiResponse<AdminCreateUserResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request) {
        AdminCreateUserResponse result = userService.adminCreateUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user role, department or full name")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequest request) {
        AdminUserDetailResponse result = userService.adminUpdateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/lock")
    @Operation(summary = "Lock user account — user cannot log in")
    public ResponseEntity<ApiResponse<AdminUserStatusResponse>> lockUser(@PathVariable Long id) {
        AdminUserStatusResponse result = userService.lockUser(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Unlock user account")
    public ResponseEntity<ApiResponse<AdminUserStatusResponse>> unlockUser(@PathVariable Long id) {
        AdminUserStatusResponse result = userService.unlockUser(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reset user password to a new temporary password and send via email")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(@PathVariable Long id) {
        userService.resetUserPassword(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Temporary password sent to user email")));
    }
}
