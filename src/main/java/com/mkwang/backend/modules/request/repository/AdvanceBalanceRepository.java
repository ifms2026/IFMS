package com.mkwang.backend.modules.request.repository;

import com.mkwang.backend.modules.request.entity.AdvanceBalance;
import com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdvanceBalanceRepository extends JpaRepository<AdvanceBalance, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AdvanceBalance> {
  List<AdvanceBalance> findByUserIdOrderByIdAsc(Long userId, org.springframework.data.domain.Pageable pageable);

  Optional<AdvanceBalance> findByAdvanceRequest_Id(Long requestId);

  List<AdvanceBalance> findByAdvanceRequest_Project_IdAndStatusNot(Long projectId, AdvanceBalanceStatus status);

  /**
   * All outstanding/partially-settled advances for a user.
   * Used to populate the dropdown when creating a REIMBURSE request.
   */
  List<AdvanceBalance> findByUserIdAndStatusNot(Long userId, AdvanceBalanceStatus status);

  /**
   * Total remaining debt across all unsettled advances for a user.
   * Used by payroll to compute advance_deduct.
   */
  @Query("SELECT COALESCE(SUM(ab.remainingAmount), 0) FROM AdvanceBalance ab " +
         "WHERE ab.user.id = :userId AND ab.status <> 'SETTLED'")
  BigDecimal sumRemainingByUserId(@Param("userId") Long userId);

  /** Outstanding advance balance grouped by employee, restricted to the supplied employees. */
  @Query("""
      SELECT ab.user.id, COALESCE(SUM(ab.remainingAmount), 0)
      FROM AdvanceBalance ab
      WHERE ab.status <> 'SETTLED' AND ab.remainingAmount > 0
        AND ab.user.id IN :userIds
      GROUP BY ab.user.id
      """)
  List<Object[]> sumRemainingByUserIds(@Param("userIds") List<Long> userIds);

  /** Outstanding advance balance grouped by employee, limited to projects managed by a team leader. */
  @Query("""
      SELECT ab.user.id, COALESCE(SUM(ab.remainingAmount), 0)
      FROM AdvanceBalance ab
      WHERE ab.status <> 'SETTLED' AND ab.remainingAmount > 0
        AND ab.user.id IN :userIds
        AND ab.advanceRequest.project.id IN :projectIds
      GROUP BY ab.user.id
      """)
  List<Object[]> sumRemainingByUserIdsAndProjectIds(
      @Param("userIds") List<Long> userIds,
      @Param("projectIds") List<Long> projectIds);

  @Query("""
      SELECT ab.advanceRequest.project.id, ab.advanceRequest.phase.id, ab.advanceRequest.category.id,
             COALESCE(SUM(ab.remainingAmount), 0)
      FROM AdvanceBalance ab
      WHERE ab.status <> 'SETTLED' AND ab.remainingAmount > 0
        AND ab.advanceRequest.project IS NOT NULL
      GROUP BY ab.advanceRequest.project.id, ab.advanceRequest.phase.id, ab.advanceRequest.category.id
      """)
  List<Object[]> sumOutstandingByProjectPhaseCategory();

  /**
   * All unsettled advances for a user — pessimistic lock for payroll deduction.
   * Locks all rows to prevent concurrent REIMBURSE while payroll is running.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ab FROM AdvanceBalance ab WHERE ab.user.id = :userId AND ab.status <> 'SETTLED' ORDER BY ab.createdAt, ab.id")
  List<AdvanceBalance> findUnsettledByUserIdForUpdate(@Param("userId") Long userId);

  /**
   * Fetch with pessimistic lock for single-record settlement (REIMBURSE or manual return).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ab FROM AdvanceBalance ab WHERE ab.id = :id")
  Optional<AdvanceBalance> findByIdForUpdate(@Param("id") Long id);

  Optional<AdvanceBalance> findByAdvanceRequestId(Long requestId);

  @EntityGraph(attributePaths = {"user", "user.department", "advanceRequest", "advanceRequest.project",
      "advanceRequest.phase", "advanceRequest.category"})
  List<AdvanceBalance> findByStatusNotOrderByCreatedAtAscIdAsc(AdvanceBalanceStatus status);

  @Query("""
      SELECT COALESCE(SUM(ab.remainingAmount), 0) FROM AdvanceBalance ab
      WHERE ab.status <> 'SETTLED'
        AND ab.user.department.id = :deptId
      """)
  java.math.BigDecimal sumOutstandingByDeptId(@Param("deptId") Long deptId);

  @Query("""
      SELECT COUNT(DISTINCT ab.user.id) FROM AdvanceBalance ab
      WHERE ab.status <> 'SETTLED'
        AND ab.remainingAmount > 0
        AND ab.user.department.id = :deptId
      """)
  long countEmployeesWithDebtByDeptId(@Param("deptId") Long deptId);

  /**
   * Top debtors system-wide.
   * Returns Object[] per group: [userId, fullName, deptName, totalRemaining, oldestCreatedAt].
   * Caller computes daysSince from oldestCreatedAt.
   */
  @Query("""
      SELECT ab.user.id,
             ab.user.fullName,
             ab.user.department.name,
             COALESCE(SUM(ab.remainingAmount), 0),
             MIN(ab.createdAt)
      FROM AdvanceBalance ab
      WHERE ab.status <> 'SETTLED' AND ab.remainingAmount > 0
      GROUP BY ab.user.id, ab.user.fullName, ab.user.department.name
      ORDER BY COALESCE(SUM(ab.remainingAmount), 0) DESC
      """)
  List<Object[]> findTopDebtorsSystemWide(org.springframework.data.domain.Pageable pageable);
}
