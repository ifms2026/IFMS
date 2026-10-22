package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    @Query("select t from Transaction t where t.status='SUCCESS' and t.type in ('SYSTEM_TOPUP','ADVANCE_RETURN') and lower(t.transactionCode) like lower(concat('%',:code,'%')) order by t.id desc")
    org.springframework.data.domain.Page<Transaction> findHistoricalAccountingSources(@Param("code") String code, org.springframework.data.domain.Pageable page);
    java.util.Optional<com.mkwang.backend.modules.wallet.entity.Transaction> findByOriginalTransactionId(Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Transaction t where t.id=:id")
    java.util.Optional<com.mkwang.backend.modules.wallet.entity.Transaction> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    java.util.Optional<com.mkwang.backend.modules.wallet.entity.Transaction> findFirstByTypeAndPaymentRef(
            com.mkwang.backend.modules.wallet.entity.TransactionType type, String paymentRef);


  Optional<Transaction> findByTransactionCode(String transactionCode);


  boolean existsByTransactionCode(String transactionCode);

  Optional<Transaction> findByPaymentRefAndStatus(String paymentRef, TransactionStatus status);

  List<Transaction> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
      ReferenceType referenceType, Long referenceId);

  @Query("SELECT DISTINCT t FROM Transaction t JOIN t.entries e WHERE t.id = :transactionId AND e.wallet.id = :walletId")
  Optional<Transaction> findOwnedTransactionById(@Param("transactionId") Long transactionId,
                                                 @Param("walletId") Long walletId);
}
