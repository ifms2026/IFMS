package com.mkwang.backend.modules.audit.config;

import com.mkwang.backend.modules.audit.listener.FinancialAuditListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.context.annotation.Configuration;

/**
 * HibernateListenerRegistryConfig — đăng ký FinancialAuditListener với Hibernate.
 * <p>
 * Sử dụng @PostConstruct để đảm bảo:
 * 1. EntityManagerFactory đã được Spring khởi tạo hoàn tất
 * 2. FinancialAuditListener đã được Spring tạo như một bean (có RabbitTemplate injected)
 * Sau đó mới unwrap SessionFactoryImpl và append listener.
 * <p>
 * Lý do không dùng {@code @EntityListeners} trên từng Entity:
 * - {@code @EntityListeners} chỉ hỗ trợ JPA-style lifecycle callbacks (Pre/PostPersist...)
 *   không hỗ trợ Hibernate-specific PostCommit events.
 * - PostCommit events (chỉ fire khi DB commit thành công) chỉ có trong Hibernate API.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HibernateListenerRegistryConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final FinancialAuditListener financialAuditListener;

    @PostConstruct
    public void registerHibernateListeners() {
        // Unwrap JPA EntityManagerFactory → Hibernate SessionFactoryImpl
        SessionFactoryImpl sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImpl.class);

        // Lấy EventListenerRegistry từ Hibernate ServiceRegistry
        EventListenerRegistry registry = sessionFactory
                .getServiceRegistry()
                .getService(EventListenerRegistry.class);

        // Đăng ký listener vào 3 Post-Commit event groups
        // appendListeners() THÊM VÀO CUỐI danh sách, không ghi đè listener mặc định của Hibernate
        registry.appendListeners(EventType.POST_COMMIT_INSERT, financialAuditListener);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, financialAuditListener);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, financialAuditListener);

        log.info("[HibernateListenerRegistryConfig] FinancialAuditListener registered " +
                "for POST_COMMIT_INSERT, POST_COMMIT_UPDATE, POST_COMMIT_DELETE");
    }
}
