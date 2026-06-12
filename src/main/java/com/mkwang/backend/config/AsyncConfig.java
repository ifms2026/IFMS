package com.mkwang.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AsyncConfig — bật hỗ trợ @Async toàn application.
 * Cần thiết để AuditPublisher.publishAsync() thực sự chạy bất đồng bộ.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
