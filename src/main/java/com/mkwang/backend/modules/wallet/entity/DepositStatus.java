package com.mkwang.backend.modules.wallet.entity;

public enum DepositStatus {
    PENDING,    // Payment URL created, awaiting VNPay IPN
    COMPLETED,  // IPN success — wallet credited
    FAILED      // IPN failure response
}
