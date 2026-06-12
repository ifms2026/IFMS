package com.mkwang.backend.modules.wallet.entity;

/**
 * Direction of a transaction relative to the wallet it belongs to.
 * Every transaction record is always from the perspective of one wallet.
 * A fund transfer creates two records: DEBIT on the source wallet, CREDIT on the destination wallet.
 */
public enum TransactionDirection {
  CREDIT,  // Tiền vào ví (balance tăng)
  DEBIT    // Tiền ra khỏi ví (balance giảm)
}
