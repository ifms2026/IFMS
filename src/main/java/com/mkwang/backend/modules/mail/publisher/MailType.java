package com.mkwang.backend.modules.mail.publisher;

/**
 * MailType — phân loại các loại email trong hệ thống.
 * Dùng làm classifier trong Strategy Pattern của MailPublisher.
 */
public enum MailType {

    /** Email chào mừng khi user được tạo mới / onboard vào hệ thống. */
    ONBOARD,

    /** Email cảnh báo: PIN bị khóa, tài khoản bị lock, ngưỡng chi tiêu vượt quá... */
    WARNING,

    FORGET_PASSWORD
}
