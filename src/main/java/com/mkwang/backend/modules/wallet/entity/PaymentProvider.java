package com.mkwang.backend.modules.wallet.entity;

/**
 * Enum representing the payment gateway provider.
 * Matches Database.md transactions.gateway_provider field.
 */
public enum PaymentProvider {
    PAYOS,      // Cổng VietQR (PayOS)
    MOMO,       // Ví điện tử MoMo
    VNPAY,      // Cổng VNPay
    MOCK_BANK,  // Rút tiền qua MockBank Corporate Banking API
    INTERNAL    // Giao dịch nội bộ (hoàn ứng, trả lương, cấp vốn)
}
