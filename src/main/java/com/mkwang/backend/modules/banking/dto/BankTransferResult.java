package com.mkwang.backend.modules.banking.dto;

/**
 * Envelope response from MockBank — mirrors the VCB-style API format.
 *
 * <pre>
 * {
 *   "responseCode":    "00",              // "00" = success; "09" = duplicate; others = failed
 *   "responseMessage": "Giao dich thanh cong",
 *   "data": { ... }                       // null when failed
 * }
 * </pre>
 */
public record BankTransferResult(
    String responseCode,
    String responseMessage,
    BankTransferData data     // null on FAILED; populated on SUCCESS or DUPLICATE
) {
    /** Transfer successful — money has left the corporate account. */
    public boolean isSuccess() {
        return "00".equals(responseCode);
    }

    /**
     * Idempotency duplicate — same referenceNumber was already processed.
     * Treat as success: extract transactionId from data.
     */
    public boolean isDuplicate() {
        return "09".equals(responseCode);
    }

    /** Convenience: get the bank transaction ID from the data payload. */
    public String transactionId() {
        return data != null ? data.transactionId() : null;
    }
}
