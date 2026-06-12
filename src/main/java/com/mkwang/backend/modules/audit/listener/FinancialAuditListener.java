package com.mkwang.backend.modules.audit.listener;

import com.mkwang.backend.modules.audit.context.AuditContextHolder;
import com.mkwang.backend.modules.audit.dto.AuditMessageDTO;
import com.mkwang.backend.modules.audit.entity.AuditLog;
import com.mkwang.backend.modules.audit.publisher.AuditPublisher;
import com.mkwang.backend.modules.audit.strategy.DeleteAuditStrategy;
import com.mkwang.backend.modules.audit.strategy.InsertAuditStrategy;
import com.mkwang.backend.modules.audit.strategy.UpdateAuditStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * FinancialAuditListener — Hibernate Post-Commit event listener.
 * <p>
 * Chỉ kích hoạt SAU KHI transaction DB commit thành công 100%.
 * <p>
 * Flow:
 * 
 * <pre>
 *   DB Operation commits
 *     → Hibernate fires PostCommit event (on request thread)
 *         → Strategy.extract() → AuditMessageDTO  [~microseconds, CPU only]
 *             → AuditPublisher.publishAsync()  [returns IMMEDIATELY]
 *                 → audit-pub-* thread → RabbitMQ  [không block request thread]
 *                     → AuditLogConsumer → save audit_logs
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialAuditListener implements
        PostCommitInsertEventListener,
        PostCommitUpdateEventListener,
        PostCommitDeleteEventListener {

    private final AuditPublisher auditPublisher; // @Async → không block request thread
    private final InsertAuditStrategy insertStrategy;
    private final UpdateAuditStrategy updateStrategy;
    private final DeleteAuditStrategy deleteStrategy;

    private static final Set<Class<?>> BLACKLISTED = Set.of(
            AuditLog.class);

    // ── POST_COMMIT_INSERT ────────────────────────────────────────

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (isBlacklisted(event.getEntity()))
            return;
        try {
            AuditMessageDTO dto = insertStrategy.extract(
                    getEntityName(event.getPersister()),
                    String.valueOf(event.getId()),
                    AuditContextHolder.getActorId(),
                    event.getPersister().getPropertyNames(),
                    null,
                    event.getState());
            auditPublisher.publishAsync(dto);
        } catch (Exception ex) {
            log.error("[FinancialAuditListener] INSERT audit failed for {}: {}",
                    event.getEntity().getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
        log.debug("[FinancialAuditListener] INSERT commit failed for: {}",
                event.getEntity().getClass().getSimpleName());
    }

    // ── POST_COMMIT_UPDATE ────────────────────────────────────────

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (isBlacklisted(event.getEntity()))
            return;
        try {
            AuditMessageDTO dto = updateStrategy.extract(
                    getEntityName(event.getPersister()),
                    String.valueOf(event.getId()),
                    AuditContextHolder.getActorId(),
                    event.getPersister().getPropertyNames(),
                    event.getOldState(),
                    event.getState());
            if (dto.oldValues() == null && dto.newValues() == null)
                return;
            auditPublisher.publishAsync(dto);
        } catch (Exception ex) {
            log.error("[FinancialAuditListener] UPDATE audit failed for {}: {}",
                    event.getEntity().getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
        log.debug("[FinancialAuditListener] UPDATE commit failed for: {}",
                event.getEntity().getClass().getSimpleName());
    }

    // ── POST_COMMIT_DELETE ────────────────────────────────────────

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (isBlacklisted(event.getEntity()))
            return;
        try {
            AuditMessageDTO dto = deleteStrategy.extract(
                    getEntityName(event.getPersister()),
                    String.valueOf(event.getId()),
                    AuditContextHolder.getActorId(),
                    event.getPersister().getPropertyNames(),
                    event.getDeletedState(),
                    null);
            auditPublisher.publishAsync(dto);
        } catch (Exception ex) {
            log.error("[FinancialAuditListener] DELETE audit failed for {}: {}",
                    event.getEntity().getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
        log.debug("[FinancialAuditListener] DELETE commit failed for: {}",
                event.getEntity().getClass().getSimpleName());
    }

    // ── RequiresPostCommitHandling ────────────────────────────────

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean isBlacklisted(Object entity) {
        return BLACKLISTED.contains(entity.getClass());
    }

    private String getEntityName(EntityPersister persister) {
        String fullName = persister.getEntityName();
        int dotIndex = fullName.lastIndexOf('.');
        return dotIndex >= 0 ? fullName.substring(dotIndex + 1) : fullName;
    }
}
