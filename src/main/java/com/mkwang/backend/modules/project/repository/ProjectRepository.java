package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    java.util.Optional<Project> findByIdAndDepartment_Id(Long projectId, Long departmentId);

    List<Project> findByDepartment_IdOrderByCreatedAtDesc(Long departmentId);

    List<Project> findByDepartment_IdAndStatusOrderByCreatedAtDesc(Long departmentId, ProjectStatus status);

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findByStatusOrderByCreatedAtDesc(ProjectStatus status);

    @Query("""
            select distinct p
            from Project p
            join p.members pm
            where pm.user.id = :userId
              and (:status is null or p.status = :status)
            order by p.createdAt desc
            """)
    List<Project> findMemberProjects(
            @Param("userId") Long userId,
            @Param("status") ProjectStatus status
    );

    @Query("""
            select distinct p
            from Project p
            left join fetch p.currentPhase
            join p.members pm
            where pm.user.id = :leaderId
              and pm.projectRole = 'LEADER'
              and (:status is null or p.status = :status)
              and (lower(p.name) like :searchLike or lower(p.projectCode) like :searchLike)
            order by p.createdAt desc
            """)
    List<Project> findLeaderProjects(
            @Param("leaderId") Long leaderId,
            @Param("status") ProjectStatus status,
            @Param("searchLike") String searchLike
    );

    @Query("""
            select count(pm)
            from ProjectMember pm
            where pm.project.id = :projectId
            """)
    int countMembersByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(SUM(p.totalBudget), 0) FROM Project p WHERE p.department.id = :deptId")
    java.math.BigDecimal sumTotalBudgetByDeptId(@Param("deptId") Long deptId);

    @Query("SELECT COALESCE(SUM(p.availableBudget), 0) FROM Project p WHERE p.department.id = :deptId")
    java.math.BigDecimal sumAvailableBudgetByDeptId(@Param("deptId") Long deptId);

    @Query("SELECT p.status, COUNT(p) FROM Project p WHERE p.department.id = :deptId GROUP BY p.status")
    java.util.List<Object[]> countByStatusForDept(@Param("deptId") Long deptId);
}
