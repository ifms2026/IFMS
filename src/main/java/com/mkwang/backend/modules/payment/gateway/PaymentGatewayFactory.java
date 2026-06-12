package com.mkwang.backend.modules.payment.gateway;

import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import com.mkwang.backend.modules.payment.exception.UnsupportedPaymentGatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PaymentGatewayFactory {

    private final Map<PaymentGateway, PaymentGatewayService> gatewayServices;

    public PaymentGatewayFactory(List<PaymentGatewayService> services) {
        this.gatewayServices = new EnumMap<>(PaymentGateway.class);
        services.forEach(service -> gatewayServices.put(service.getGatewayType(), service));
        log.info("Loaded payment gateways: {}", gatewayServices.keySet());
    }

    public PaymentGatewayService resolve(PaymentGateway gateway) {
        PaymentGatewayService service = gatewayServices.get(gateway);
        if (service == null) {
            throw new UnsupportedPaymentGatewayException(String.valueOf(gateway));
        }
        return service;
    }
}

