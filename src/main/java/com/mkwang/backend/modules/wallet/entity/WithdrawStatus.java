package com.mkwang.backend.modules.wallet.entity;

/**
 * Lifecycle states of a WithdrawRequest.
 *
 * State machine:
 *   PENDING    → PROCESSING  (Accountant bắt đầu xử lý)
 *   PENDING    → CANCELLED   (User tự hủy)
 *   PENDING    → REJECTED    (Accountant từ chối)
 *   PROCESSING → COMPLETED   (MockBank SUCCESS)
 *   PROCESSING → FAILED      (MockBank FAILED)
 *
 * Wallet side-effects:
 *   PENDING:    lockedBalance += amount
 *   COMPLETED:  lockedBalance -= amount, balance -= amount  (via wallet.settle())
 *   FAILED:     lockedBalance -= amount                     (via wallet.unlock())
 *   REJECTED:   lockedBalance -= amount                     (via wallet.unlock())
 *   CANCELLED:  lockedBalance -= amount                     (via wallet.unlock())
 */
public enum WithdrawStatus {
    PENDING,      // Vừa tạo — lockedBalance += amount
    PROCESSING,   // Accountant đang xử lý
    COMPLETED,    // MockBank SUCCESS — lockedBalance -= amount, balance -= amount
    FAILED,       // MockBank FAILED — lockedBalance -= amount (unlock, không debit)
    REJECTED,     // Accountant từ chối — lockedBalance -= amount
    CANCELLED     // User tự hủy khi PENDING — lockedBalance -= amount
}
