package com.mkwang.backend.modules.banking.service;

import com.mkwang.backend.modules.banking.dto.BankTransferResult;

import java.math.BigDecimal;

/**
 * BankingService — HTTP client abstraction for MockBank Corporate Banking API.
 *
 * Encapsulates all outbound calls to MockBank so that WithdrawService
 * only needs to know the outcome (BankTransferResult), not HTTP internals.
 */
public interface BankingService {

    /**
     * Initiate a bank transfer from IFMS corporate account to a user's personal bank account.
     * Calls MockBank POST /v1/transfers.
     *
     * @param creditAccount     User's bank account number (e.g. "0011004000005")
     * @param creditAccountName User's account holder name
     * @param creditBankCode    Bank code (e.g. "VCB", "TCB")
     * @param amount            Amount to transfer (must be > 0)
     * @param referenceNumber   Idempotency key — use WithdrawRequest.withdrawCode
     * @param description       Free-text description for the bank statement
     * @return BankTransferResult with responseCode + optional transactionId
     */
    BankTransferResult transfer(String creditAccount,
                                String creditAccountName,
                                String creditBankCode,
                                BigDecimal amount,
                                String referenceNumber,
                                String description);

    /**
     * Look up a previous transaction by referenceNumber.
     * Calls MockBank GET /v1/transactions/{referenceNumber}.
     * Used to re-check after a network timeout.
     *
     * @param referenceNumber The idempotency key (withdrawCode) sent to MockBank
     * @return BankTransferResult; throws InternalSystemException if not found or error
     */
    BankTransferResult getTransaction(String referenceNumber);
}
