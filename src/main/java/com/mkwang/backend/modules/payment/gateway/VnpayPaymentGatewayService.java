package com.mkwang.backend.modules.payment.gateway;

import com.mkwang.backend.modules.payment.config.VnpayProperties;
import com.mkwang.backend.modules.payment.dto.request.PaymentCancelRequest;
import com.mkwang.backend.modules.payment.dto.request.PaymentRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.PaymentCancelResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentResponse;
import com.mkwang.backend.modules.payment.dto.response.PaymentStatusResponse;
import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import com.mkwang.backend.modules.payment.exception.InvalidPaymentSignatureException;
import com.mkwang.backend.modules.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayPaymentGatewayService implements AdvancedPaymentGatewayService {

    private static final String HMAC_SHA512 = "HmacSHA512";
    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime expiredAt = now.plusMinutes(resolveExpireMinutes(request));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", properties.getCommand());
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", request.amount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CreateDate", now.format(VNP_DATE_FORMAT));
        params.put("vnp_ExpireDate", expiredAt.format(VNP_DATE_FORMAT));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", resolveIpAddress(request));
        params.put("vnp_Locale", resolveLocale(request));
        params.put("vnp_OrderInfo", request.depositInfo());
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_ReturnUrl", resolveReturnUrl(request));
        params.put("vnp_TxnRef", request.depositCode());


        if (request.bankCode() != null && !request.bankCode().isBlank()) {
            params.put("vnp_BankCode", request.bankCode());
        }

        String secureHash = sign(params);
        String paymentUrl = properties.getPaymentUrl() + "?" + buildQuery(params) + "&vnp_SecureHash=" + secureHash;

        log.info("Created VNPay payment URL for depositCode={}, amount={}", request.depositCode(), request.amount());

        return PaymentResponse.builder()
                .gateway(PaymentGateway.VNPAY)
                .depositCode(request.depositCode())
                .transactionRef(request.depositCode())
                .paymentUrl(paymentUrl)
                .status("PENDING")
                .message("Payment URL generated")
                .expiredAt(expiredAt)
                .build();
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        if (!verifySignature(params)) {
            throw new InvalidPaymentSignatureException();
        }

        String responseCode = params.getOrDefault("vnp_ResponseCode", "99");
        String transactionStatus = params.getOrDefault("vnp_TransactionStatus", "99");
        boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);

        BigDecimal amount = parseAmount(params.get("vnp_Amount"));
        LocalDateTime paidAt = parseDate(params.get("vnp_PayDate"));

        log.info("VNPay callback processed for orderCode={}, success={}, responseCode={}",
                params.get("vnp_TxnRef"), success, responseCode);

        return PaymentCallbackResult.builder()
                .gateway(PaymentGateway.VNPAY)
                .success(success)
                .message(success ? "Payment completed" : "Payment failed")
                .orderCode(params.get("vnp_TxnRef"))
                .transactionRef(params.get("vnp_TxnRef"))
                .gatewayTransactionId(params.get("vnp_TransactionNo"))
                .responseCode(responseCode)
                .transactionStatus(transactionStatus)
                .amount(amount)
                .paidAt(paidAt)
                .rawParams(new TreeMap<>(params))
                .build();
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        String receivedSecureHash = params.get("vnp_SecureHash");
        if (receivedSecureHash == null || receivedSecureHash.isBlank()) {
            return false;
        }

        Map<String, String> signData = new TreeMap<>(params);
        signData.remove("vnp_SecureHash");
        signData.remove("vnp_SecureHashType");

        String expectedSecureHash = sign(signData);
        return expectedSecureHash.equalsIgnoreCase(receivedSecureHash);
    }

    @Override
    public PaymentGateway getGatewayType() {
        return PaymentGateway.VNPAY;
    }

    @Override
    public PaymentCancelResponse cancelPayment(PaymentCancelRequest request) {
        throw new PaymentException("VNPay redirect flow does not support cancel operation in this integration");
    }

    @Override
    public PaymentStatusResponse checkStatus(String transactionRef) {
        throw new PaymentException("VNPay redirect flow does not support status query in this integration");
    }

    private String sign(Map<String, String> params) {
        try {
            String dataToSign = buildHashData(params);
            Mac mac = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKeySpec = new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            mac.init(secretKeySpec);
            byte[] hashBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                sb.append(String.format("%02x", hashByte));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new PaymentException("Failed to sign VNPay payload: " + ex.getMessage());
        }
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        // VNPay expects application/x-www-form-urlencoded style where spaces are encoded as '+'
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String resolveIpAddress(PaymentRequest request) {
        if (request.ipAddress() == null || request.ipAddress().isBlank()) {
            return "127.0.0.1";
        }
        return request.ipAddress();
    }

    private String resolveLocale(PaymentRequest request) {
        if (request.locale() == null || request.locale().isBlank()) {
            return properties.getLocale();
        }
        return request.locale();
    }

    private String resolveReturnUrl(PaymentRequest request) {
        if (request.returnUrl() != null && !request.returnUrl().isBlank()) {
            return request.returnUrl();
        }

        if (properties.getReturnUrl() != null && !properties.getReturnUrl().isBlank()) {
            return properties.getReturnUrl();
        }

        if (properties.getIpnUrl() != null && !properties.getIpnUrl().isBlank()) {
            log.warn("VNPay returnUrl is empty, fallback to ipnUrl for testing: {}", properties.getIpnUrl());
            return properties.getIpnUrl();
        }

        throw new PaymentException("VNPay returnUrl is required (or fallback ipnUrl must be configured)");
    }

    private long resolveExpireMinutes(PaymentRequest request) {
        if (request.expireMinutes() == null || request.expireMinutes() <= 0) {
            return properties.getDefaultExpireMinutes();
        }
        return request.expireMinutes();
    }

    private BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(amount).movePointLeft(2);
    }

    private LocalDateTime parseDate(String vnpDate) {
        if (vnpDate == null || vnpDate.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(vnpDate, VNP_DATE_FORMAT);
    }
}


