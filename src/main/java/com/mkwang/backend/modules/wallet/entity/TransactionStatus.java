package com.mkwang.backend.modules.wallet.entity;

/**
 * Lifecycle status of a transaction.
 *
 * Normal flow : PENDING → SUCCESS
 * Failure flow: PENDING → FAILED
 * Cancel flow : PENDING → CANCELLED
 *
 * A SUCCESS transaction is immutable and must never be deleted.
 * FAILED / CANCELLED transactions are kept for audit purposes.
 */
public enum TransactionStatus {
  SUCCESS,    // Giao dịch hoàn thành, số dư đã thay đổi
  PENDING,    // Đang chờ xử lý — balance chưa thay đổi, lockedBalance đã tăng
  FAILED,     // Giao dịch thất bại (lỗi gateway, không đủ số dư...)
  CANCELLED   // Giao dịch PENDING bị huỷ trước khi xử lý (do user hoặc hệ thống)
}
