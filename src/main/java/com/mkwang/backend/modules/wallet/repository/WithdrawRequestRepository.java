package com.mkwang.backend.modules.wallet.repository;

import com.mkwang.backend.modules.wallet.entity.WithdrawRequest;
import com.mkwang.backend.modules.wallet.entity.WithdrawStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Long> {

    /** All requests by a specific user, ordered newest first. */
    Page<WithdrawRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** All requests filtered by status — for Accountant list view. */
    Page<WithdrawRequest> findByStatusOrderByCreatedAtAsc(WithdrawStatus status, Pageable pageable);

    /** All requests across all users — for Accountant management. */
    Page<WithdrawRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
