package com.mkwang.backend.modules.request.repository;

import com.mkwang.backend.modules.request.entity.AdvanceBalance;
import com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdvanceBalanceRepository extends JpaRepository<AdvanceBalance, Long> {

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

  /**
   * All unsettled advances for a user — pessimistic lock for payroll deduction.
   * Locks all rows to prevent concurrent REIMBURSE while payroll is running.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ab FROM AdvanceBalance ab WHERE ab.user.id = :userId AND ab.status <> 'SETTLED'")
  List<AdvanceBalance> findUnsettledByUserIdForUpdate(@Param("userId") Long userId);

  /**
   * Fetch with pessimistic lock for single-record settlement (REIMBURSE or manual return).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ab FROM AdvanceBalance ab WHERE ab.id = :id")
  Optional<AdvanceBalance> findByIdForUpdate(@Param("id") Long id);

  Optional<AdvanceBalance> findByAdvanceRequestId(Long requestId);

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
