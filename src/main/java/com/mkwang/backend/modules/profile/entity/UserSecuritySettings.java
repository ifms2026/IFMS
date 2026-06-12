package com.mkwang.backend.modules.profile.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UserSecuritySettings entity - Stores transaction PIN and security settings.
 * One-to-One relationship with User (Shares primary key via @MapsId).
 */
@Entity
@Table(name = "user_security_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecuritySettings extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "transaction_pin", length = 100)
    private String transactionPin;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    public boolean isPinLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    public void resetRetryCount() {
        this.retryCount = 0;
        this.lockedUntil = null;
    }

    public void lockPin(int minutes) {
        this.lockedUntil = LocalDateTime.now().plusMinutes(minutes);
    }
}
