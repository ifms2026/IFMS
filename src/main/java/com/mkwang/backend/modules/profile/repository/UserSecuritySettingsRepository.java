package com.mkwang.backend.modules.profile.repository;

import com.mkwang.backend.modules.profile.entity.UserSecuritySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSecuritySettingsRepository extends JpaRepository<UserSecuritySettings, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from UserSecuritySettings s where s.userId=:id")
    java.util.Optional<UserSecuritySettings> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
