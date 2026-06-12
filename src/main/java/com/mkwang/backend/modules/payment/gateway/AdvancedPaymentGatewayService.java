package com.mkwang.backend.modules.payment.gateway;

import com.mkwang.backend.modules.payment.dto.request.PaymentCancelRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCancelResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentStatusResponse;

public interface AdvancedPaymentGatewayService extends PaymentGatewayService {

    default boolean supportsCancel() {
        return false;
    }

    default PaymentCancelResponse cancelPayment(PaymentCancelRequest request) {
        throw new UnsupportedOperationException("Cancel payment is not supported");
    }

    default boolean supportsStatusCheck() {
        return false;
    }

    default PaymentStatusResponse checkStatus(String transactionRef) {
        throw new UnsupportedOperationException("Status check is not supported");
    }
}

