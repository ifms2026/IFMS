package com.mkwang.backend.modules.payment.service;

import com.mkwang.backend.modules.payment.dto.request.PaymentCancelRequest;
import com.mkwang.backend.modules.payment.dto.request.PaymentRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.PaymentCancelResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentStatusResponse;
import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import com.mkwang.backend.modules.payment.exception.PaymentOperationNotSupportedException;
import com.mkwang.backend.modules.payment.gateway.AdvancedPaymentGatewayService;
import com.mkwang.backend.modules.payment.gateway.PaymentGatewayFactory;
import com.mkwang.backend.modules.payment.gateway.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentGatewayFactory paymentGatewayFactory;

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        PaymentGatewayService gatewayService = paymentGatewayFactory.resolve(request.gateway());
        log.info("Creating payment with gateway={}, orderCode={}", request.gateway(), request.depositCode());
        return gatewayService.createPayment(request);
    }

    @Override
    public PaymentCallbackResult handleCallback(PaymentGateway gateway, Map<String, String> params) {
        PaymentGatewayService gatewayService = paymentGatewayFactory.resolve(gateway);
        log.info("Handling callback for gateway={}, txRef={}", gateway, params.get("vnp_TxnRef"));
        return gatewayService.handleCallback(params);
    }

    @Override
    public PaymentCancelResponse cancelPayment(PaymentCancelRequest request) {
        PaymentGatewayService gatewayService = paymentGatewayFactory.resolve(request.gateway());
        if (!(gatewayService instanceof AdvancedPaymentGatewayService advancedGatewayService)
                || !advancedGatewayService.supportsCancel()) {
            throw new PaymentOperationNotSupportedException("Cancel payment", request.gateway().name());
        }

        log.info("Cancelling payment for gateway={}, txRef={}", request.gateway(), request.transactionRef());
        return advancedGatewayService.cancelPayment(request);
    }

    @Override
    public PaymentStatusResponse checkStatus(PaymentGateway gateway, String transactionRef) {
        PaymentGatewayService gatewayService = paymentGatewayFactory.resolve(gateway);
        if (!(gatewayService instanceof AdvancedPaymentGatewayService advancedGatewayService)
                || !advancedGatewayService.supportsStatusCheck()) {
            throw new PaymentOperationNotSupportedException("Check payment status", gateway.name());
        }

        log.info("Checking payment status for gateway={}, txRef={}", gateway, transactionRef);
        return advancedGatewayService.checkStatus(transactionRef);
    }
}

