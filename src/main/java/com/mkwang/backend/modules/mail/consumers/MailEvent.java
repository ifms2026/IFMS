package com.mkwang.backend.modules.mail.consumers;


public record MailEvent(
        String to,
        String subject,
        String content
) {

}
