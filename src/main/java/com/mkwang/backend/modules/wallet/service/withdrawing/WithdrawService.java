package com.mkwang.backend.modules.wallet.service.withdrawing;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.wallet.dto.request.CreateWithdrawRequest;
import com.mkwang.backend.modules.wallet.dto.response.WithdrawRequestResponse;
import com.mkwang.backend.modules.wallet.entity.WithdrawStatus;
import org.springframework.data.domain.Pageable;

/**
 * WithdrawService — orchestrates the full withdrawal lifecycle.
 *
 * Flow:
 *   createRequest()   → funds locked → if amount <= role limit: auto-execute via MockBank
 *                                    → else: PENDING (accountant must execute)
 *   executeWithdraw() → Accountant calls MockBank → COMPLETED or FAILED
 *   rejectRequest()   → Accountant rejects → REJECTED, funds unlocked
 *   cancelRequest()   → User cancels while PENDING → CANCELLED, funds unlocked
 */
public interface WithdrawService {

    /**
     * User submits a withdrawal request.
     * Bank info is auto-read from UserProfile.
     * Funds locked immediately. If amount <= WITHDRAW_LIMIT_{ROLE} in SystemConfig,
     * MockBank is called right away (auto-execute) → status will be COMPLETED or FAILED.
     * Otherwise status stays PENDING until accountant runs executeWithdraw().
     */
    WithdrawRequestResponse createRequest(Long userId, CreateWithdrawRequest request);

    /**
     * Accountant executes a PENDING withdrawal via MockBank.
     * Sets executedBy + executedAt. On SUCCESS: wallet.settle() + FLOAT_MAIN-.
     * On FAILED: wallet.unlock().
     */
    WithdrawRequestResponse executeWithdraw(Long accountantId, Long requestId, String note);

    /**
     * Accountant rejects a PENDING withdrawal.
     * Funds are unlocked (returned to available balance).
     *
     * @param reason Rejection reason (required)
     */
    WithdrawRequestResponse rejectRequest(Long accountantId, Long requestId, String reason);

    /**
     * User cancels their own PENDING request.
     * Funds are unlocked.
     */
    WithdrawRequestResponse cancelRequest(Long userId, Long requestId);

    /**
     * User views their own withdrawal history.
     */
    PageResponse<WithdrawRequestResponse> getMyRequests(Long userId, Pageable pageable);

    /**
     * Accountant views all withdrawal requests, optionally filtered by status.
     */
    PageResponse<WithdrawRequestResponse> getAllRequests(WithdrawStatus status, Pageable pageable);
}

