package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.Wallet;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Wallet entity.
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

  Optional<Wallet> findByOwnerTypeAndOwnerId(WalletOwnerType ownerType, Long ownerId);

  boolean existsByOwnerTypeAndOwnerId(WalletOwnerType ownerType, Long ownerId);

  /**
   * Pessimistic write lock — use this before any balance mutation.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.ownerType = :ownerType AND w.ownerId = :ownerId")
  Optional<Wallet> findByOwnerTypeAndOwnerIdForUpdate(
      @Param("ownerType") WalletOwnerType ownerType,
      @Param("ownerId") Long ownerId
  );

  /**
   * Sum of all wallet balances for a given owner type.
   * Used for reconciliation breakdown (total dept wallets, total project wallets, etc.)
   */
  @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.ownerType = :ownerType")
  java.math.BigDecimal sumBalancesByType(@Param("ownerType") WalletOwnerType ownerType);

  /**
   * Sum of all wallet balances EXCLUDING a specific owner type.
   * Used to compute the expected FLOAT_MAIN balance.
   */
  @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.ownerType != :excludeType")
  java.math.BigDecimal sumAllBalancesExcept(@Param("excludeType") WalletOwnerType excludeType);

  @Query("""
      SELECT w.ownerId, COALESCE(w.lockedBalance, 0)
      FROM Wallet w
      WHERE w.ownerType = 'USER'
        AND w.ownerId IN :userIds
      """)
  java.util.List<Object[]> findLockedBalancesForUsers(@Param("userIds") java.util.List<Long> userIds);
}
