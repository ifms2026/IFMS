package com.mkwang.backend.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publish external side effects only after durable state commits. */
@Slf4j
public final class AfterCommit {
    private AfterCommit() {}

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safely(action); }
            });
        } else {
            safely(action);
        }
    }

    private static void safely(Runnable action) {
        try { action.run(); }
        catch (Exception failure) { log.warn("After-commit notification failed: {}", failure.getClass().getSimpleName()); }
    }
}
