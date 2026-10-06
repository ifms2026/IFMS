package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.AccountingJournal;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountingJournalRepository extends JpaRepository<AccountingJournal, Long>, JpaSpecificationExecutor<AccountingJournal> {
    @EntityGraph(attributePaths = "lines")
    @Query("select j from AccountingJournal j where j.id = :id")
    Optional<AccountingJournal> findDetailById(@Param("id") Long id);

    Optional<AccountingJournal> findByEventTypeAndSourceTypeAndSourceId(
            AccountingJournalEvent eventType, String sourceType, String sourceId);

    List<AccountingJournal> findByWalletTransactionIdOrderByIdAsc(Long walletTransactionId);

    List<AccountingJournal> findByRequestIdOrderByIdAsc(Long requestId);

    @Query("select distinct j from AccountingJournal j left join j.lines l " +
            "where j.advanceBalanceId = :advanceBalanceId or l.advanceBalanceId = :advanceBalanceId " +
            "order by j.postingDate, j.id")
    List<AccountingJournal> findActivitiesForAdvance(@Param("advanceBalanceId") Long advanceBalanceId);
}
