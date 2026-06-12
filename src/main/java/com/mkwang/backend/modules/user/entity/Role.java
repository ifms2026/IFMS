package com.mkwang.backend.modules.user.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Role entity - Represents a user role with associated permissions.
 * Admin can CRUD roles via UI and assign permissions to each role.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /**
     * Check if this role has a specific permission
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Add a permission to this role
     */
    public void addPermission(Permission permission) {
        permissions.add(permission);
    }

    /**
     * Remove a permission from this role
     */
    public void removePermission(Permission permission) {
        permissions.remove(permission);
    }
}
