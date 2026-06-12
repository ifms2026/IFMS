package com.mkwang.backend.config;

import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@RequiredArgsConstructor
public class JpaAuditingConfig {

    private final UserRepository userRepository;

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return new AuditorAware<Long>() {
            @Override
            public Optional<Long> getCurrentAuditor() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                // Check if user is not authenticated
                if (authentication == null ||
                        !authentication.isAuthenticated() ||
                        authentication instanceof AnonymousAuthenticationToken) {
                    return Optional.empty();
                }

                Object principal = authentication.getPrincipal();

                // 1. JWT Authentication - Principal is UserDetailsAdapter
                if (principal instanceof UserDetailsAdapter) {
                    UserDetailsAdapter userDetails = (UserDetailsAdapter) principal;
                    return Optional.ofNullable(userDetails.getUser().getId());
                }

                // 2. OAuth2 Authentication - Principal is OAuth2User (Google login)
                if (principal instanceof OAuth2User) {
                    OAuth2User oauth2User = (OAuth2User) principal;
                    String email = oauth2User.getAttribute("email");

                    if (email != null) {
                        return userRepository.findByEmail(email)
                                .map(User::getId);
                    }
                }

                // 3. Fallback - try to get from username (email)
                String username = authentication.getName();
                if (username != null && !"anonymousUser".equals(username)) {
                    return userRepository.findByEmail(username)
                            .map(User::getId);
                }

                return Optional.empty();
            }
        };
    }
}
