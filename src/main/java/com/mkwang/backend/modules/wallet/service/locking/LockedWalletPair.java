package com.mkwang.backend.modules.wallet.service.locking;

import com.mkwang.backend.modules.wallet.entity.Wallet;

/**
 * Pair of already-locked wallets mapped to source and destination roles.
 */
public record LockedWalletPair(Wallet source, Wallet dest) {
}


