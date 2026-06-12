package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.DepositLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepositLogRepository extends JpaRepository<DepositLog, Long>,
        JpaSpecificationExecutor<DepositLog> {
    Optional<DepositLog> findByDepositCode(String depositCode);
}
