package com.mkwang.backend.modules.audit.strategy;

import com.mkwang.backend.modules.audit.dto.AuditMessageDTO;

/**
 * AuditExtractionStrategy — Strategy interface cho Diff Engine.
 * <p>
 * Mỗi implementation xử lý một loại Hibernate event:
 * <ul>
 *   <li>{@link InsertAuditStrategy} — POST_COMMIT_INSERT</li>
 *   <li>{@link UpdateAuditStrategy} — POST_COMMIT_UPDATE (chỉ dirty fields)</li>
 *   <li>{@link DeleteAuditStrategy} — POST_COMMIT_DELETE</li>
 * </ul>
 * Convention gọi từ FinancialAuditListener:
 * <ul>
 *   <li>INSERT: {@code extract(..., oldState=null, newState=insertedState)}</li>
 *   <li>UPDATE: {@code extract(..., oldState=oldState, newState=newState)}</li>
 *   <li>DELETE: {@code extract(..., oldState=deletedState, newState=null)}</li>
 * </ul>
 */
public interface AuditExtractionStrategy {

    /**
     * Bóc tách dữ liệu từ Hibernate event và tạo AuditMessageDTO.
     *
     * @param entityName    tên class entity (simple name)
     * @param entityId      ID entity dạng String
     * @param actorId       ID người thao tác (từ AuditContextHolder, nullable)
     * @param propertyNames tên các property từ Hibernate EntityPersister
     * @param oldState      trạng thái trước thay đổi (null cho INSERT)
     * @param newState      trạng thái sau thay đổi (null cho DELETE)
     * @return AuditMessageDTO ready để gửi vào RabbitMQ
     */
    AuditMessageDTO extract(String entityName, String entityId, Long actorId,
                            String[] propertyNames, Object[] oldState, Object[] newState);
}
