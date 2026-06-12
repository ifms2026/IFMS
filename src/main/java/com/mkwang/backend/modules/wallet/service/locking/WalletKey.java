package com.mkwang.backend.modules.wallet.service.locking;

import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;

/**
 * Comparable wallet identifier used to enforce deterministic lock ordering.
 */
public record WalletKey(WalletOwnerType ownerType, Long ownerId) implements Comparable<WalletKey> {

    @Override
    public int compareTo(WalletKey other) {
        int typeCompare = this.ownerType.name().compareTo(other.ownerType.name());
        if (typeCompare != 0) {
            return typeCompare;
        }
        return this.ownerId.compareTo(other.ownerId);
    }
}


