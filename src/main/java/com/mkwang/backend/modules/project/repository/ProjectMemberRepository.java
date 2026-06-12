package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.ProjectMember;
import com.mkwang.backend.modules.project.entity.ProjectMemberId;
import com.mkwang.backend.modules.project.entity.ProjectRole;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, ProjectMemberId>,
                JpaSpecificationExecutor<ProjectMember> {

    boolean existsByProject_IdAndUser_Id(Long projectId, Long userId);

    boolean existsByProject_IdAndUser_IdAndProjectRole(Long projectId, Long userId, ProjectRole projectRole);

    @Query("SELECT pm.project.id FROM ProjectMember pm WHERE pm.user.id = :userId AND pm.projectRole = 'LEADER'")
    List<Long> findProjectIdsByLeader(@Param("userId") Long userId);

    Optional<ProjectMember> findByProject_IdAndUser_Id(Long projectId, Long userId);

    List<ProjectMember> findByProject_IdAndProjectRole(Long projectId, ProjectRole projectRole);

    List<ProjectMember> findByUser_IdAndProject_Department_IdOrderByJoinedAtDesc(Long userId, Long departmentId);

    @Query("""
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project p
            WHERE pm.id.projectId = :projectId
              AND pm.id.userId = :userId
            """)
    Optional<ProjectMember> findWithProjectByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.user u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            WHERE pm.project.id = :projectId
            ORDER BY pm.joinedAt ASC
            """)
    List<ProjectMember> findMembersWithProfileByProjectId(@Param("projectId") Long projectId);

    @Query("""
            SELECT pm.project.id, COUNT(pm)
            FROM ProjectMember pm
            WHERE pm.project.id IN :projectIds
            GROUP BY pm.project.id
            """)
    List<Object[]> countMembersByProjectIds(@Param("projectIds") List<Long> projectIds);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            WHERE u.department.id = :departmentId
              AND u.status = 'ACTIVE'
              AND u.id NOT IN (
                  SELECT pm.user.id FROM ProjectMember pm WHERE pm.project.id = :projectId
              )
              AND (lower(u.fullName) like :searchLike OR lower(up.employeeCode) like :searchLike)
            ORDER BY u.fullName ASC
            """)
    List<User> findAvailableUsersForProject(
            @Param("departmentId") Long departmentId,
            @Param("projectId") Long projectId,
            @Param("searchLike") String searchLike
    );

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END
            FROM com.mkwang.backend.modules.request.entity.Request r
            WHERE r.requester.id = :userId
              AND r.project.id IN :projectIds
              AND r.status IN ('PENDING', 'APPROVED_BY_TEAM_LEADER')
            """)
    boolean hasPendingRequestsInProjects(
            @Param("userId") Long userId,
            @Param("projectIds") List<Long> projectIds
    );

    @Query("""
            SELECT DISTINCT pm.project.id FROM ProjectMember pm
            WHERE pm.project.id IN :projectIds
              AND pm.user.id = :userId
              AND pm.projectRole = 'MEMBER'
            """)
    List<Long> findMemberProjectIds(
            @Param("userId") Long userId,
            @Param("projectIds") List<Long> projectIds
    );
}



