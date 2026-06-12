package com.mkwang.backend.modules.user.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.profile.entity.UserSecuritySettings;
import jakarta.persistence.*;
import lombok.*;

/**
 * User entity - Core authentication and identity entity.
 * Has relationships with Role (ManyToOne), Department (ManyToOne),
 * UserProfile (OneToOne), UserSecuritySettings (OneToOne).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "is_first_login", nullable = false)
    @Builder.Default
    private Boolean isFirstLogin = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Token version — tăng 1 mỗi lần login mới.
     * So sánh với claim "ver" trong JWT để enforce single-session.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 0;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserSecuritySettings securitySettings;

    /**
     * Check if user account is active
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
