package com.mkwang.backend.common.sse;

public enum SseEventType {

    CONNECTED("connected"),
    NOTIFICATION("notification"),
    WALLET_UPDATED("wallet.updated"),
    TRANSACTION_CREATED("transaction.created");

    private final String value;

    SseEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
