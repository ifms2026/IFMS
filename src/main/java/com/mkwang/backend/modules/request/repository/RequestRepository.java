package com.mkwang.backend.modules.request.repository;

import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.request.entity.RequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {

    @Query("""
            SELECT r.status, COUNT(r) FROM Request r
            WHERE r.requester.id = :userId
            GROUP BY r.status
            """)
    List<Object[]> countByStatusForUser(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.project
            LEFT JOIN FETCH r.phase
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.requester
            LEFT JOIN FETCH r.attachments att
            LEFT JOIN FETCH att.file
            WHERE r.id = :id AND r.requester.id = :userId
            """)
    Optional<Request> findDetailByIdAndRequesterId(
            @Param("id") Long id,
            @Param("userId") Long userId);

    @Query("""
            SELECT h FROM RequestHistory h
            LEFT JOIN FETCH h.actor
            WHERE h.request.id = :requestId
            ORDER BY h.createdAt ASC
            """)
    List<RequestHistory> findHistoriesByRequestId(@Param("requestId") Long requestId);

    Optional<Request> findByIdAndRequesterId(Long id, Long requesterId);

    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.requester u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            LEFT JOIN FETCH r.project
            LEFT JOIN FETCH r.phase
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.attachments att
            LEFT JOIN FETCH att.file
            WHERE r.id = :id AND r.project.id IN :projectIds
              AND r.requester.id <> :leaderId
            """)
    Optional<Request> findDetailByIdForTl(
            @Param("id") Long id,
            @Param("leaderId") Long leaderId,
            @Param("projectIds") List<Long> projectIds);

    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.requester u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            LEFT JOIN FETCH r.project p
            LEFT JOIN FETCH p.department
            LEFT JOIN FETCH r.attachments att
            LEFT JOIN FETCH att.file
            WHERE r.id = :id
              AND r.type = 'PROJECT_TOPUP'
              AND p.department.id = :departmentId
            """)
    Optional<Request> findDetailByIdForManager(
            @Param("id") Long id,
            @Param("departmentId") Long departmentId);

    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.requester u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            LEFT JOIN FETCH u.department
            LEFT JOIN FETCH r.project
            LEFT JOIN FETCH r.phase
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.advanceBalance
            LEFT JOIN FETCH r.attachments att
            LEFT JOIN FETCH att.file
            WHERE r.id = :id
              AND r.status = 'APPROVED_BY_TEAM_LEADER'
              AND r.type IN ('ADVANCE', 'EXPENSE', 'REIMBURSE')
            """)
    Optional<Request> findDetailByIdForAccountant(@Param("id") Long id);

    @Query("""
            SELECT r FROM Request r
            LEFT JOIN FETCH r.requester u
            LEFT JOIN FETCH u.profile up
            LEFT JOIN FETCH up.avatarFile
            LEFT JOIN FETCH u.department
            WHERE r.id = :id
              AND r.type = 'DEPARTMENT_TOPUP'
            """)
    Optional<Request> findDetailByIdForCfo(@Param("id") Long id);

    @Query("""
            SELECT r.requester.id, COUNT(r)
            FROM Request r
            WHERE r.requester.id IN :userIds
              AND r.status IN ('PENDING', 'APPROVED_BY_TEAM_LEADER')
            GROUP BY r.requester.id
            """)
    List<Object[]> countPendingByRequesterIds(@Param("userIds") List<Long> userIds);

    @Query("""
            SELECT COUNT(r)
            FROM Request r
            WHERE r.requester.id = :userId
              AND r.status IN ('PENDING', 'APPROVED_BY_TEAM_LEADER')
            """)
    int countPendingForRequester(@Param("userId") Long userId);

    /**
     * Count pending requests for a specific member scoped to a set of projects.
     * Used by Team Leader team-members list to populate pendingRequestsCount.
     * "Pending" = PENDING or APPROVED_BY_TEAM_LEADER (not yet disbursed).
     */
    @Query("""
            SELECT COUNT(r) FROM Request r
            WHERE r.requester.id = :userId
              AND r.project.id IN :projectIds
              AND r.status IN ('PENDING', 'APPROVED_BY_TEAM_LEADER')
            """)
    int countPendingForMemberInProjects(
            @Param("userId") Long userId,
            @Param("projectIds") List<Long> projectIds
    );

    /**
     * Top-10 most recent requests for a member scoped to a set of projects.
     * Used by Team Leader team-members detail to populate recentRequests.
     */
    @Query("""
            SELECT r FROM Request r
            LEFT JOIN FETCH r.project p
            LEFT JOIN FETCH r.category
            WHERE r.requester.id = :userId
              AND p.id IN :projectIds
            ORDER BY r.createdAt DESC
            """)
    List<Request> findTop10RecentByRequesterInProjects(
            @Param("userId") Long userId,
            @Param("projectIds") List<Long> projectIds,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            SELECT COUNT(r) FROM Request r
            WHERE r.type = 'PROJECT_TOPUP'
              AND r.status = 'PENDING'
              AND r.project.department.id = :deptId
            """)
    long countPendingProjectTopupByDeptId(@Param("deptId") Long deptId);

    @Query("""
            SELECT COUNT(r) FROM Request r
            WHERE r.status = 'APPROVED_BY_TEAM_LEADER'
              AND r.type IN ('ADVANCE', 'EXPENSE', 'REIMBURSE')
            """)
    long countPendingDisbursements();

    @Query("""
            SELECT COUNT(r) FROM Request r
            WHERE r.type = 'DEPARTMENT_TOPUP'
              AND r.status = 'PENDING'
            """)
    long countPendingDeptTopup();

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM Request r
            WHERE r.type = 'DEPARTMENT_TOPUP'
              AND r.status IN ('APPROVED_BY_CFO', 'PAID')
              AND YEAR(r.updatedAt) = :year
              AND MONTH(r.updatedAt) = :month
            """)
    java.math.BigDecimal sumMonthlyApprovedDeptTopup(@Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT COUNT(r) FROM Request r
            WHERE r.type = 'DEPARTMENT_TOPUP'
              AND r.status = 'REJECTED'
              AND YEAR(r.updatedAt) = :year
              AND MONTH(r.updatedAt) = :month
            """)
    long countMonthlyRejectedDeptTopup(@Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT r FROM Request r
            LEFT JOIN FETCH r.requester u
            LEFT JOIN FETCH u.department
            WHERE r.type = 'DEPARTMENT_TOPUP'
            ORDER BY r.createdAt DESC
            """)
    List<Request> findRecentDeptTopup(org.springframework.data.domain.Pageable pageable);

    /**
     * Monthly spending aggregation for an employee's paid requests.
     * Returns rows: [year(int), month(int), type(RequestType), sum(BigDecimal)]
     * Used for the employee spending analytics chart.
     */
    @Query("""
            SELECT EXTRACT(YEAR FROM r.paidAt),
                   EXTRACT(MONTH FROM r.paidAt),
                   r.type,
                   COALESCE(SUM(r.approvedAmount), 0)
            FROM Request r
            WHERE r.requester.id = :userId
              AND r.status = :status
              AND r.type IN :types
              AND r.paidAt >= :from
              AND r.paidAt <= :to
            GROUP BY EXTRACT(YEAR FROM r.paidAt), EXTRACT(MONTH FROM r.paidAt), r.type
            ORDER BY EXTRACT(YEAR FROM r.paidAt), EXTRACT(MONTH FROM r.paidAt)
            """)
    List<Object[]> sumPaidByMonthAndType(
            @Param("userId") Long userId,
            @Param("status") RequestStatus status,
            @Param("types") List<RequestType> types,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}


