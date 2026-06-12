package com.mkwang.backend.modules.payment.service;

import com.mkwang.backend.modules.payment.dto.request.PaymentCancelRequest;
import com.mkwang.backend.modules.payment.dto.request.PaymentRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.PaymentCancelResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentStatusResponse;
import com.mkwang.backend.modules.payment.enums.PaymentGateway;

import java.util.Map;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    PaymentCallbackResult handleCallback(PaymentGateway gateway, Map<String, String> params);

    PaymentCancelResponse cancelPayment(PaymentCancelRequest request);

    PaymentStatusResponse checkStatus(PaymentGateway gateway, String transactionRef);
}

