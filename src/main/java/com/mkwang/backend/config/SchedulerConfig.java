package com.mkwang.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * SchedulerConfig — cấu hình thread pool cho @Scheduled tasks.
 * <p>
 * @Async executor cho audit publish được định nghĩa riêng trong
 * AuditRabbitMQConfig#auditPublishExecutor() với bean name "auditPublishExecutor".
 * AsyncConfig bật @EnableAsync toàn app để AuditPublisher hoạt động đúng.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    /**
     * Scheduler pool — cho @Scheduled tasks (cron jobs, periodic cleanup).
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("ifms-scheduler-");
        taskScheduler.initialize();
        taskRegistrar.setScheduler(taskScheduler);
    }
}
