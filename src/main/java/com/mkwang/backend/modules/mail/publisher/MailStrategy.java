package com.mkwang.backend.modules.mail.publisher;

/**
 * MailStrategy — interface chiến lược gửi mail.
 * <p>
 * Mỗi implementation xử lý một loại email cụ thể (OnBoard, Warning, v.v.),
 * đóng gói logic routing key và queue riêng của từng loại.
 * <p>
 * Các implementation được Spring inject vào {@link MailPublisher} và
 * được lựa chọn tự động dựa theo {@link MailType}.
 */
public interface MailStrategy {

    /**
     * Loại email mà strategy này xử lý.
     * Dùng làm key để MailPublisher dispatch đúng strategy.
     */
    MailType getType();

    /**
     * Gửi email theo strategy của loại này.
     *
     * @param to      địa chỉ người nhận
     * @param subject tiêu đề email
     * @param content nội dung HTML
     */
    void publish(String to, String subject, String content);
}
