package com.mkwang.backend.modules.auth.security;

import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.entity.UserStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
@Getter
public class UserDetailsAdapter implements UserDetails {

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Add ROLE authority (e.g., ROLE_ADMIN)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));

        // Add all PERMISSION authorities from the role
        user.getRole().getPermissions()
                .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));

        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Không dùng tính năng account expire
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Không dùng tính năng password expire
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}
