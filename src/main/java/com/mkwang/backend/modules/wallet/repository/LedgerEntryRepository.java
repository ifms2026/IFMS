package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, JpaSpecificationExecutor<LedgerEntry> {

  @EntityGraph(attributePaths = {"transaction", "wallet"})
  Page<LedgerEntry> findAll(Specification<LedgerEntry> spec, Pageable pageable);

  /**
   * Paginated transaction history for a wallet — used for statement display.
   */
  Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

  /**
   * Paginated transaction history with date-range filter.
   */
  @Query("SELECT e FROM LedgerEntry e WHERE e.wallet.id = :walletId " +
         "AND e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
  Page<LedgerEntry> findByWalletIdAndDateRange(
      @Param("walletId") Long walletId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);

  /**
   * All entries for a transaction — used to verify double-entry integrity.
   */
  List<LedgerEntry> findByTransactionId(Long transactionId);

  /**
   * Latest entry for a wallet — used to get current balance_after snapshot.
   */
  @Query("SELECT e FROM LedgerEntry e WHERE e.wallet.id = :walletId ORDER BY e.createdAt DESC LIMIT 1")
  Optional<LedgerEntry> findLatestByWalletId(@Param("walletId") Long walletId);

  /**
   * Batch-fetch ledger entries for a set of transactions scoped to one wallet.
   * Used by the accountant ledger list to compute signed amount and balanceAfter
   * from the CompanyFund perspective without N+1 queries.
   */
  @Query("SELECT e FROM LedgerEntry e WHERE e.transaction.id IN :transactionIds AND e.wallet.id = :walletId")
  List<LedgerEntry> findByTransactionIdsAndWalletId(
      @Param("transactionIds") List<Long> transactionIds,
      @Param("walletId") Long walletId);

  /**
   * Sum of CREDIT entries for a wallet in a date range — accountant inflow.
   */
  @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
         "WHERE e.wallet.id = :walletId AND e.direction = 'CREDIT' " +
         "AND e.createdAt >= :from AND e.createdAt <= :to")
  java.math.BigDecimal sumCreditByWalletAndRange(
      @Param("walletId") Long walletId,
      @Param("from") java.time.LocalDateTime from,
      @Param("to") java.time.LocalDateTime to);

  /**
   * Sum of DEBIT entries for a wallet in a date range — accountant outflow.
   */
  @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
         "WHERE e.wallet.id = :walletId AND e.direction = 'DEBIT' " +
         "AND e.createdAt >= :from AND e.createdAt <= :to")
  java.math.BigDecimal sumDebitByWalletAndRange(
      @Param("walletId") Long walletId,
      @Param("from") java.time.LocalDateTime from,
      @Param("to") java.time.LocalDateTime to);

  /**
   * Count of distinct transactions touching a wallet in a date range.
   */
  @Query("SELECT COUNT(DISTINCT e.transaction.id) FROM LedgerEntry e " +
         "WHERE e.wallet.id = :walletId AND e.createdAt >= :from AND e.createdAt <= :to")
  long countTransactionsByWalletAndRange(
      @Param("walletId") Long walletId,
      @Param("from") java.time.LocalDateTime from,
      @Param("to") java.time.LocalDateTime to);

  /**
   * All entries for a transaction with wallet eagerly fetched — for detail view.
   */
  @Query("SELECT e FROM LedgerEntry e JOIN FETCH e.wallet WHERE e.transaction.id = :transactionId")
  List<LedgerEntry> findByTransactionIdWithWallet(@Param("transactionId") Long transactionId);

  /**
   * Sum of DEBIT amounts grouped by wallet.ownerId, for a specific ownerType and date range.
   * Returns List<Object[]> where [0]=ownerId (Long), [1]=totalDebited (BigDecimal).
   * Used by Admin analytics to compute department spending for the period.
   */
  @Query("SELECT e.wallet.ownerId, COALESCE(SUM(e.amount), 0) " +
         "FROM LedgerEntry e " +
         "WHERE e.wallet.ownerType = :ownerType " +
         "AND e.direction = 'DEBIT' " +
         "AND e.createdAt >= :from AND e.createdAt <= :to " +
         "GROUP BY e.wallet.ownerId")
  List<Object[]> sumDebitGroupedByOwnerAndRange(
      @Param("ownerType") com.mkwang.backend.modules.wallet.entity.WalletOwnerType ownerType,
      @Param("from") java.time.LocalDateTime from,
      @Param("to") java.time.LocalDateTime to);
}
