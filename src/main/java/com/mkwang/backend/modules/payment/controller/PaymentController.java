package com.mkwang.backend.modules.payment.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.payment.dto.request.PaymentCancelRequest;
import com.mkwang.backend.modules.payment.dto.request.PaymentRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.PaymentCancelResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentStatusResponse;
import com.mkwang.backend.modules.payment.dto.response.VnpayIpnResponse;
import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import com.mkwang.backend.modules.payment.exception.InvalidPaymentSignatureException;
import com.mkwang.backend.modules.payment.exception.UnsupportedPaymentGatewayException;
import com.mkwang.backend.modules.payment.service.PaymentService;
import com.mkwang.backend.modules.wallet.service.depositing.DepositService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final DepositService depositService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Create payment successfully", paymentService.createPayment(request)));
    }

    /**
     * VNPay IPN — server-to-server callback. Public endpoint (no JWT).
     * Response MUST be {"RspCode":"00","Message":"Confirm Success"} — VNPay retries until it sees this.
     */
    @GetMapping("/{gateway}/ipn")
    public ResponseEntity<VnpayIpnResponse> ipnCallback(
            @PathVariable String gateway,
            @RequestParam Map<String, String> params,
            HttpServletRequest request
    ) {
        Map<String, String> logParams = new HashMap<>(params);
        logParams.remove("vnp_SecureHash");
        log.info("IPN received: gateway={} remoteIp={} txRef={} responseCode={} paramsWithoutHash={}",
                gateway, request.getRemoteAddr(), params.get("vnp_TxnRef"),
                params.get("vnp_ResponseCode"), logParams);

        try {
            PaymentGateway paymentGateway = parseGateway(gateway);
            PaymentCallbackResult result = paymentService.handleCallback(paymentGateway, params);
            VnpayIpnResponse ipnResponse = depositService.processIpn(result);
            log.info("IPN processed: txRef={} rspCode={}", params.get("vnp_TxnRef"), ipnResponse.rspCode());
            return ResponseEntity.ok(ipnResponse);
        } catch (InvalidPaymentSignatureException e) {
            log.warn("IPN invalid signature: txRef={}", params.get("vnp_TxnRef"));
            return ResponseEntity.ok(VnpayIpnResponse.invalidSig());
        } catch (Exception e) {
            log.error("IPN processing error: txRef={} error={}", params.get("vnp_TxnRef"), e.getMessage(), e);
            return ResponseEntity.ok(VnpayIpnResponse.invalidSig());
        }
    }

    @GetMapping("/{gateway}/return")
    public ResponseEntity<ApiResponse<PaymentCallbackResult>> returnCallback(
            @PathVariable String gateway,
            @RequestParam Map<String, String> params
    ) {
        PaymentGateway paymentGateway = parseGateway(gateway);
        PaymentCallbackResult result = paymentService.handleCallback(paymentGateway, params);
        return ResponseEntity.ok(ApiResponse.success("Handle callback successfully", result));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancelPayment(
            @Valid @RequestBody PaymentCancelRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Cancel payment processed", paymentService.cancelPayment(request)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> checkStatus(
            @RequestParam PaymentGateway gateway,
            @RequestParam String transactionRef
    ) {
        PaymentStatusResponse response = paymentService.checkStatus(gateway, transactionRef);
        return ResponseEntity.ok(ApiResponse.success("Payment status retrieved", response));
    }

    private PaymentGateway parseGateway(String gateway) {
        try {
            return PaymentGateway.valueOf(gateway.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedPaymentGatewayException(gateway);
        }
    }
}
