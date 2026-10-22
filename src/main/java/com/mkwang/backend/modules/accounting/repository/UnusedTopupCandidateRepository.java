package com.mkwang.backend.modules.accounting.repository;
import com.mkwang.backend.modules.accounting.entity.UnusedTopupCandidate;
import org.springframework.data.jpa.repository.*;
public interface UnusedTopupCandidateRepository extends JpaRepository<UnusedTopupCandidate,Long>,JpaSpecificationExecutor<UnusedTopupCandidate> {}
