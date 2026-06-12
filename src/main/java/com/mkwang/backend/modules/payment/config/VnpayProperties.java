package com.mkwang.backend.modules.payment.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.vnpay")
public class VnpayProperties {

    @NotBlank
    private String tmnCode;

    @NotBlank
    private String hashSecret;

    @NotBlank
    private String paymentUrl;

    private String returnUrl;

    private String ipnUrl;
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
    private String locale = "vn";
    private long defaultExpireMinutes = 15;
}

