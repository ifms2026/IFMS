package com.mkwang.backend.modules.wallet.service.depositing;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.VnpayIpnResponse;
import com.mkwang.backend.modules.wallet.dto.request.CreateDepositRequest;
import com.mkwang.backend.modules.wallet.dto.response.DepositLogResponse;
import com.mkwang.backend.modules.wallet.entity.DepositStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface DepositService {

    /**
     * User initiates a deposit. Generates a depositCode (= vnp_TxnRef),
     * creates a DepositLog(PENDING), and returns the VNPay payment URL.
     */
    DepositLogResponse createDeposit(Long userId, CreateDepositRequest request, String ipAddress);

    /**
     * Called by PaymentController when VNPay IPN arrives (server-to-server).
     * Idempotency-safe: if depositLog is already COMPLETED or FAILED, returns alreadyDone().
     * On success: credits USER wallet + updates FLOAT_MAIN.
     *
     * Returns the VNPay-protocol response that must be sent back verbatim.
     */
    VnpayIpnResponse processIpn(PaymentCallbackResult result);

    /**
     * User views their own deposit history with optional filters.
     */
    PageResponse<DepositLogResponse> getMyDeposits(Long userId, DepositStatus status, LocalDate from, LocalDate to, Pageable pageable);
}
