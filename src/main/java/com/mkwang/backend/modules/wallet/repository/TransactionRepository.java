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

  Optional<Transaction> findByTransactionCode(String transactionCode);

  boolean existsByTransactionCode(String transactionCode);

  Optional<Transaction> findByPaymentRefAndStatus(String paymentRef, TransactionStatus status);

  List<Transaction> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
      ReferenceType referenceType, Long referenceId);

  @Query("SELECT DISTINCT t FROM Transaction t JOIN t.entries e WHERE t.id = :transactionId AND e.wallet.id = :walletId")
  Optional<Transaction> findOwnedTransactionById(@Param("transactionId") Long transactionId,
                                                 @Param("walletId") Long walletId);
}
