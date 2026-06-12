package com.mkwang.backend.modules.banking.dto;

import java.math.BigDecimal;

/**
 * Nested data payload from MockBank when a transfer succeeds or a duplicate is found.
 * Maps to MockBank response envelope's "data" field.
 */
public record BankTransferData(
    String transactionId,       // "VCB20260405103000000001"
    String referenceNumber,     // echoes the idempotency key sent by IFMS
    BigDecimal remainingBalance,
    String transactionTime
) {}
