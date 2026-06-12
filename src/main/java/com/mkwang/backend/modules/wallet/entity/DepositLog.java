package com.mkwang.backend.modules.wallet.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DepositLog — tracks each VNPay deposit attempt.
 *
 * depositCode == vnp_TxnRef sent to VNPay. Used as the idempotency key
 * in processIpn() to locate which userId to credit.
 */
@Entity
@Table(name = "deposit_logs", indexes = {
        @Index(name = "idx_dl_user_id",      columnList = "user_id"),
        @Index(name = "idx_dl_deposit_code", columnList = "deposit_code")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deposit_code", unique = true, nullable = false, length = 30)
    private String depositCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepositStatus status;

    @Column(name = "vnp_transaction_no", length = 50)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 5)
    private String vnpResponseCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
