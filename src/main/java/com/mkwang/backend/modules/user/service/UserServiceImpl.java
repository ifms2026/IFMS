package com.mkwang.backend.modules.user.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.ConflictException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.mail.publisher.MailPublisher;
import com.mkwang.backend.modules.mail.publisher.MailType;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.organization.repository.DepartmentRepository;
import com.mkwang.backend.modules.profile.entity.UserSecuritySettings;
import com.mkwang.backend.modules.profile.repository.UserSecuritySettingsRepository;
import com.mkwang.backend.modules.profile.service.ProfileService;
import com.mkwang.backend.modules.user.dto.request.AdminCreateUserRequest;
import com.mkwang.backend.modules.user.dto.request.AdminUpdateUserRequest;
import com.mkwang.backend.modules.user.dto.request.OnboardUserRequest;
import com.mkwang.backend.modules.user.dto.response.AdminCreateUserResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserDetailResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserStatusResponse;
import com.mkwang.backend.modules.user.dto.response.AdminUserSummaryResponse;
import com.mkwang.backend.modules.user.dto.response.OnboardUserResponse;
import com.mkwang.backend.modules.user.entity.*;
import com.mkwang.backend.modules.user.mapper.UserMapper;
import com.mkwang.backend.modules.user.repository.RoleRepository;
import com.mkwang.backend.modules.user.repository.UserRepository;
import com.mkwang.backend.modules.user.repository.UserSpecification;
import com.mkwang.backend.modules.wallet.entity.Wallet;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.repository.WalletRepository;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.security.SecureRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository                 userRepository;
    private final RoleRepository                 roleRepository;
    private final DepartmentRepository           departmentRepository;
    private final ProfileService                 profileService;
    private final WalletService                  walletService;
    private final WalletRepository               walletRepository;
    private final UserSecuritySettingsRepository securitySettingsRepository;
    private final PasswordEncoder                passwordEncoder;
    private final BusinessCodeGenerator          codeGenerator;
    private final SpringTemplateEngine           templateEngine;
    private final MailPublisher                  mailPublisher;
    private final SecureRandom                   secureRandom;
    private final UserMapper                     userMapper;

    // ── POST /users/onboard ──────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public OnboardUserResponse onboardUser(OnboardUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists: " + request.getEmail());
        }

        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.getDepartmentId()));

        String employeeCode = codeGenerator.generate(BusinessCodeType.EMPLOYEE);
        String tempPassword = generateTemporaryPassword();

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(tempPassword))
                .role(role)
                .department(department)
                .status(UserStatus.ACTIVE)
                .isFirstLogin(true)
                .build();
        user = userRepository.save(user);

        profileService.createProfile(user, employeeCode, request.getJobTitle(), request.getPhoneNumber());
        walletService.createWallet(WalletOwnerType.USER, user.getId());

        sendOnboardEmail(user.getEmail(), user.getFullName(), employeeCode,
                tempPassword, role.getName(), department.getName());

        log.info("User onboarded: {} | {} | dept={}", employeeCode, request.getEmail(), department.getCode());

        return OnboardUserResponse.builder()
                .id(user.getId())
                .employeeCode(employeeCode)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(role.getName())
                .departmentName(department.getName())
                .temporaryPassword(tempPassword)
                .build();
    }

    // ── GET /users/{id} (internal) ───────────────────────────────

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getDepartmentMembers(Long departmentId, Long excludedUserId, String search, Pageable pageable) {
        var specification = UserSpecification.filterDepartmentMembers(departmentId, excludedUserId, search);
        return userRepository.findAll(specification, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public User getDepartmentUserById(Long departmentId, Long userId) {
        return userRepository.findByIdAndDepartmentIdWithProfile(userId, departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getActiveTeamLeadersByDepartmentId(Long departmentId) {
        return userRepository.findActiveTeamLeadersByDepartmentId(departmentId);
    }

    // ── GET /admin/users ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_VIEW_LIST')")
    public PageResponse<AdminUserSummaryResponse> getAdminUsers(
            String roleName, Long departmentId, String statusStr, String search, int page, int limit) {

        UserStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = UserStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status value: " + statusStr);
            }
        }

        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<User> result = userRepository.findAdminUsersOrdered(
                roleName,
                departmentId,
                status,
                search != null && !search.isBlank() ? search.trim() : "",
                pageable
        );

        List<AdminUserSummaryResponse> items = result.getContent().stream()
                .map(userMapper::toAdminSummary)
                .toList();

        return PageResponse.<AdminUserSummaryResponse>builder()
                .items(items)
                .total(result.getTotalElements())
                .page(page)
                .size(limit)
                .totalPages(result.getTotalPages())
                .build();
    }

    // ── GET /admin/users/:id ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_VIEW_LIST')")
    public AdminUserDetailResponse getAdminUserDetail(Long id) {
        User user = userRepository.findByIdWithProfile(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.USER, id)
                .orElse(null);
        UserSecuritySettings security = securitySettingsRepository.findById(id).orElse(null);

        return userMapper.toAdminDetail(user, wallet, security);
    }

    // ── POST /admin/users ────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists: " + request.getEmail());
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.getRoleId()));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
        }

        String employeeCode = codeGenerator.generate(BusinessCodeType.EMPLOYEE);
        String tempPassword = generateTemporaryPassword();

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(tempPassword))
                .role(role)
                .department(department)
                .status(UserStatus.ACTIVE)
                .isFirstLogin(true)
                .build();
        user = userRepository.save(user);

        profileService.createProfile(user, employeeCode, null, null);
        walletService.createWallet(WalletOwnerType.USER, user.getId());

        String deptName = department != null ? department.getName() : null;
        sendOnboardEmail(user.getEmail(), user.getFullName(), employeeCode,
                tempPassword, role.getName(), deptName != null ? deptName : "N/A");

        log.info("Admin created user: {} | {} | role={}", employeeCode, request.getEmail(), role.getName());

        return AdminCreateUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(role.getName())
                .departmentId(department != null ? department.getId() : null)
                .departmentName(deptName)
                .status(user.getStatus().name())
                .isFirstLogin(user.getIsFirstLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ── PUT /admin/users/:id ─────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public AdminUserDetailResponse adminUpdateUser(Long id, AdminUpdateUserRequest request) {
        User user = userRepository.findByIdWithProfile(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.getRoleId()));
            user.setRole(role);
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
            user.setDepartment(department);
        }

        userRepository.save(user);

        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.USER, id).orElse(null);
        UserSecuritySettings security = securitySettingsRepository.findById(id).orElse(null);

        return userMapper.toAdminDetail(user, wallet, security);
    }

    // ── POST /admin/users/:id/lock ───────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_LOCK')")
    public AdminUserStatusResponse lockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BadRequestException("User is already locked");
        }

        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);

        log.info("Admin locked user id={}", id);
        return AdminUserStatusResponse.builder()
                .id(user.getId())
                .status(user.getStatus().name())
                .build();
    }

    // ── POST /admin/users/:id/unlock ─────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_LOCK')")
    public AdminUserStatusResponse unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("User is already active");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("Admin unlocked user id={}", id);
        return AdminUserStatusResponse.builder()
                .id(user.getId())
                .status(user.getStatus().name())
                .build();
    }

    // ── POST /admin/users/:id/reset-password ─────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void resetUserPassword(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        String tempPassword = generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setIsFirstLogin(!"ADMIN".equals(user.getRole().getName()));
        userRepository.save(user);

        sendPasswordResetEmail(user.getEmail(), user.getFullName(), tempPassword);

        log.info("Admin reset password for user id={}", id);
    }

    // ── Cross-domain helpers ──────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long countUsersByDepartmentId(Long departmentId) {
        return userRepository.countByDepartmentId(departmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUsersByDepartmentIdWithProfile(Long departmentId) {
        return userRepository.findByDepartmentIdWithProfile(departmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return userRepository.countActiveUsers();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getActiveUsersByRoleName(String roleName) {
        return userRepository.findActiveUsersByRoleName(roleName);
    }

    // ── Private helpers ──────────────────────────────────────────

    private void sendOnboardEmail(String email, String fullName, String employeeCode,
                                   String temporaryPassword, String role, String departmentName) {
        Context ctx = new Context();
        ctx.setVariable("fullName",          fullName);
        ctx.setVariable("email",             email);
        ctx.setVariable("employeeCode",      employeeCode);
        ctx.setVariable("temporaryPassword", temporaryPassword);
        ctx.setVariable("role",              role);
        ctx.setVariable("departmentName",    departmentName);

        String html = templateEngine.process("email/onboard-email", ctx);
        mailPublisher.publish(MailType.ONBOARD, email, "Chào mừng bạn đến với IFMS — Thông tin tài khoản", html);
    }

    private void sendPasswordResetEmail(String email, String fullName, String temporaryPassword) {
        Context ctx = new Context();
        ctx.setVariable("fullName",          fullName);
        ctx.setVariable("temporaryPassword", temporaryPassword);

        String html = templateEngine.process("email/password-reset-email", ctx);
        mailPublisher.publish(MailType.FORGET_PASSWORD, email, "IFMS — Mật khẩu của bạn đã được đặt lại", html);
    }

    private String generateTemporaryPassword() {
        int digits = 1000 + secureRandom.nextInt(9000);
        return "Ifms@" + digits;
    }
}
