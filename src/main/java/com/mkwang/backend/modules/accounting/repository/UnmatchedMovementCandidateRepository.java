package com.mkwang.backend.modules.accounting.repository;
import com.mkwang.backend.modules.accounting.entity.UnmatchedMovementCandidate;
import org.springframework.data.jpa.repository.*;
public interface UnmatchedMovementCandidateRepository extends JpaRepository<UnmatchedMovementCandidate,Long>,JpaSpecificationExecutor<UnmatchedMovementCandidate> {}
