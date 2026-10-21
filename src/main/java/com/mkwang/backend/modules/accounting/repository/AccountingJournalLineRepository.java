package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.AccountingJournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountingJournalLineRepository extends JpaRepository<AccountingJournalLine, Long>,
        JpaSpecificationExecutor<AccountingJournalLine> {}
