package com.mkwang.backend.modules.user.repository;

import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            SELECT u
            FROM User u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            WHERE u.id = :userId
            """)
    Optional<User> findByIdWithProfile(@Param("userId") Long userId);

    @Query("""
            SELECT u
            FROM User u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            WHERE u.department.id = :departmentId
              AND u.status = 'ACTIVE'
              AND u.role.name = 'TEAM_LEADER'
            ORDER BY u.fullName ASC
            """)
    List<User> findActiveTeamLeadersByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("""
            SELECT u
            FROM User u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            WHERE u.id = :userId
              AND u.department.id = :departmentId
            """)
    Optional<User> findByIdAndDepartmentIdWithProfile(@Param("userId") Long userId, @Param("departmentId") Long departmentId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.department.id = :departmentId")
    long countByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = 'ACTIVE'")
    long countActiveUsers();

    @Query(
            value = """
                    SELECT u
                    FROM User u
                    LEFT JOIN FETCH u.profile up
                    LEFT JOIN FETCH up.avatarFile
                    LEFT JOIN u.role r
                    WHERE (:roleName IS NULL OR r.name = :roleName)
                      AND (:departmentId IS NULL OR u.department.id = :departmentId)
                      AND (:status IS NULL OR u.status = :status)
                      AND (
                            :search = ''
                            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(up.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    ORDER BY
                        CASE u.email
                            WHEN 'admin@ifms.vn' THEN 0
                            WHEN 'cfo@ifms.vn' THEN 1
                            WHEN 'accountant@ifms.vn' THEN 2
                            WHEN 'manager.it@ifms.vn' THEN 3
                            WHEN 'tl.it@ifms.vn' THEN 4
                            WHEN 'emp.it1@ifms.vn' THEN 5
                            WHEN 'emp.it2@ifms.vn' THEN 6
                            WHEN 'emp.sales1@ifms.vn' THEN 7
                            WHEN 'emp.fin1@ifms.vn' THEN 8
                            ELSE 100
                        END ASC,
                        u.createdAt DESC,
                        u.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(u)
                    FROM User u
                    LEFT JOIN u.profile up
                    LEFT JOIN u.role r
                    WHERE (:roleName IS NULL OR r.name = :roleName)
                      AND (:departmentId IS NULL OR u.department.id = :departmentId)
                      AND (:status IS NULL OR u.status = :status)
                      AND (
                            :search = ''
                            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(up.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    """
    )
    Page<User> findAdminUsersOrdered(
            @Param("roleName") String roleName,
            @Param("departmentId") Long departmentId,
            @Param("status") UserStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            WHERE u.department.id = :departmentId
            ORDER BY u.fullName ASC
            """)
    List<User> findByDepartmentIdWithProfile(@Param("departmentId") Long departmentId);

    @Query("SELECT u FROM User u WHERE u.role.name = :roleName AND u.status = 'ACTIVE'")
    List<User> findActiveUsersByRoleName(@Param("roleName") String roleName);
}
