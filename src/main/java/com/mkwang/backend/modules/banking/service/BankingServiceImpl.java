package com.mkwang.backend.modules.banking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.common.exception.InternalSystemException;
import com.mkwang.backend.modules.banking.dto.BankTransferResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * BankingServiceImpl — RestClient-based HTTP client for MockBank.
 *
 * All outbound calls are authenticated with Bearer API key.
 * Handles 409 DUPLICATE response (idempotency) by parsing the body
 * and returning a BankTransferResult with responseCode "09".
 */
@Slf4j
@Service
public class BankingServiceImpl implements BankingService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.banking.company-account}")
    private String companyAccount;

    public BankingServiceImpl(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.banking.base-url}") String baseUrl,
            @Value("${app.banking.api-key}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public BankTransferResult transfer(String creditAccount,
                                       String creditAccountName,
                                       String creditBankCode,
                                       BigDecimal amount,
                                       String referenceNumber,
                                       String description) {
        Map<String, Object> body = Map.of(
            "debitAccount",      companyAccount,
            "creditAccount",     creditAccount,
            "creditAccountName", creditAccountName,
            "creditBankCode",    creditBankCode,
            "amount",            amount,
            "currency",          "VND",
            "referenceNumber",   referenceNumber,
            "description",       description
        );

        log.info("[BankingService] POST /v1/transfers — ref={} amount={}", referenceNumber, amount);

        try {
            BankTransferResult result = restClient.post()
                    .uri("/v1/transfers")
                    .body(body)
                    .retrieve()
                    .body(BankTransferResult.class);

            log.info("[BankingService] Transfer result — ref={} code={}", referenceNumber,
                    result != null ? result.responseCode() : "null");
            return result;

        } catch (HttpClientErrorException.Conflict e) {
            // 409 Conflict = duplicate referenceNumber — idempotency hit
            log.warn("[BankingService] Duplicate reference detected — ref={}", referenceNumber);
            return parseResponse(e.getResponseBodyAsString());

        } catch (Exception e) {
            log.error("[BankingService] Transfer failed — ref={} error={}", referenceNumber, e.getMessage());
            throw new InternalSystemException("Không thể kết nối MockBank: " + e.getMessage());
        }
    }

    @Override
    public BankTransferResult getTransaction(String referenceNumber) {
        log.info("[BankingService] GET /v1/transactions/{}", referenceNumber);
        try {
            return restClient.get()
                    .uri("/v1/transactions/{ref}", referenceNumber)
                    .retrieve()
                    .body(BankTransferResult.class);
        } catch (Exception e) {
            log.error("[BankingService] getTransaction failed — ref={} error={}", referenceNumber, e.getMessage());
            throw new InternalSystemException("Lỗi tra cứu giao dịch MockBank: " + e.getMessage());
        }
    }

    private BankTransferResult parseResponse(String body) {
        try {
            return objectMapper.readValue(body, BankTransferResult.class);
        } catch (JsonProcessingException e) {
            log.warn("[BankingService] Failed to parse 409 response body: {}", body);
            // Return a generic duplicate indicator so caller can handle idempotency
            return new BankTransferResult("09", "Giao dich da duoc xu ly", null);
        }
    }
}
