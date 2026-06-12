package com.mkwang.backend.modules.payment.gateway;

import com.mkwang.backend.modules.payment.dto.request.PaymentRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.PaymentResponse;
import com.mkwang.backend.modules.payment.enums.PaymentGateway;

import java.util.Map;

public interface PaymentGatewayService {
    PaymentResponse createPayment(PaymentRequest request);

    PaymentCallbackResult handleCallback(Map<String, String> params);

    boolean verifySignature(Map<String, String> params);

    PaymentGateway getGatewayType();
}

