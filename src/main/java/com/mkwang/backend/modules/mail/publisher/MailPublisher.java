package com.mkwang.backend.modules.mail.publisher;

import com.mkwang.backend.common.exception.InternalSystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * MailPublisher — facade dispatcher dùng Strategy Pattern.
 * <p>
 * Inject tất cả {@link MailStrategy} implementations từ Spring context,
 * build một {@code EnumMap<MailType, MailStrategy>} tại startup.
 * <p>
 * Caller chỉ cần biết {@link MailType} — không cần biết queue nào, routing key
 * nào:
 * 
 * <pre>
 * mailPublisher.publish(MailType.ONBOARD, to, subject, content);
 * mailPublisher.publish(MailType.WARNING, to, subject, content);
 * </pre>
 * 
 * Để thêm loại email mới:
 * <ol>
 * <li>Thêm value vào {@link MailType}</li>
 * <li>Tạo {@link MailStrategy} implementation mới (@Component)</li>
 * <li>Không cần sửa MailPublisher</li>
 * </ol>
 */
@Slf4j
@Component
public class MailPublisher {

    private final Map<MailType, MailStrategy> strategies;

    public MailPublisher(List<MailStrategy> strategyList) {
        strategies = new EnumMap<>(MailType.class);
        strategyList.forEach(s -> strategies.put(s.getType(), s));
        log.info("[MailPublisher] Registered {} mail strategies: {}", strategies.size(), strategies.keySet());
    }

    /**
     * Dispatch email theo MailType.
     *
     * @param type    loại email (ONBOARD, WARNING, ...)
     * @param to      địa chỉ người nhận
     * @param subject tiêu đề
     * @param content nội dung HTML
     * @throws IllegalArgumentException nếu không có strategy cho type
     */
    public void publish(MailType type, String to, String subject, String content) {
        MailStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new InternalSystemException("No MailStrategy registered for type: " + type);
        }
        log.debug("[MailPublisher] Dispatching {} email to: {}", type, to);
        strategy.publish(to, subject, content);
    }
}
