package com.mkwang.backend.modules.profile.repository;

import com.mkwang.backend.modules.profile.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndUserIdNot(String phoneNumber, Long userId);

    @Query("""
            SELECT up FROM UserProfile up
            JOIN FETCH up.user
            WHERE up.employeeCode = :employeeCode
            """)
    Optional<UserProfile> findByEmployeeCode(@Param("employeeCode") String employeeCode);
}
