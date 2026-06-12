package com.mkwang.backend.modules.wallet.service.depositing;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.payment.dto.request.PaymentRequest;
import com.mkwang.backend.modules.payment.dto.response.PaymentCallbackResult;
import com.mkwang.backend.modules.payment.dto.response.PaymentResponse;
import com.mkwang.backend.modules.payment.dto.response.VnpayIpnResponse;
import com.mkwang.backend.modules.payment.enums.PaymentGateway;
import com.mkwang.backend.modules.payment.service.PaymentService;
import com.mkwang.backend.modules.wallet.dto.request.CreateDepositRequest;
import com.mkwang.backend.modules.wallet.dto.response.DepositLogResponse;
import com.mkwang.backend.modules.wallet.entity.DepositLog;
import com.mkwang.backend.modules.wallet.entity.DepositStatus;
import com.mkwang.backend.modules.wallet.repository.DepositLogRepository;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private final DepositLogRepository depositLogRepository;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final BusinessCodeGenerator codeGenerator;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('WALLET_DEPOSIT')")
    public DepositLogResponse createDeposit(Long userId, CreateDepositRequest request, String ipAddress) {
        String depositCode = codeGenerator.generate(BusinessCodeType.DEPOSIT);

        DepositLog depositLog = DepositLog.builder()
                .depositCode(depositCode)
                .userId(userId)
                .amount(request.amount())
                .status(DepositStatus.PENDING)
                .build();
        depositLog = depositLogRepository.save(depositLog);

        PaymentRequest paymentRequest = new PaymentRequest(
                PaymentGateway.VNPAY,
                depositCode,
                "Nap tien IFMS - " + depositCode,
                request.amount(),
                ipAddress,
                null,
                request.bankCode(),
                request.locale(),
                null
        );
        PaymentResponse paymentResponse = paymentService.createPayment(paymentRequest);

        log.info("[DepositService] Created depositCode={} userId={} amount={}",
                depositCode, userId, request.amount());
        return toResponse(depositLog, paymentResponse.getPaymentUrl());
    }

    // Called by PaymentController IPN handler — must NOT be @PreAuthorize (VNPay server has no JWT)
    @Override
    @Transactional
    public VnpayIpnResponse processIpn(PaymentCallbackResult result) {
        String depositCode = result.getOrderCode();

        DepositLog depositLog = depositLogRepository.findByDepositCode(depositCode).orElse(null);
        if (depositLog == null) {
            log.warn("[DepositService] IPN received for unknown depositCode={}", depositCode);
            return VnpayIpnResponse.notFound();
        }

        if (depositLog.getStatus() != DepositStatus.PENDING) {
            log.info("[DepositService] IPN duplicate for depositCode={} status={}", depositCode, depositLog.getStatus());
            return VnpayIpnResponse.alreadyDone();
        }

        if (result.isSuccess()) {
            walletService.deposit(
                    depositLog.getUserId(),
                    depositLog.getAmount(),
                    result.getGatewayTransactionId(),
                    depositLog.getId()
            );
            depositLog.setStatus(DepositStatus.COMPLETED);
            depositLog.setVnpTransactionNo(result.getGatewayTransactionId());
            depositLog.setVnpResponseCode(result.getResponseCode());
            depositLog.setPaidAt(result.getPaidAt());
            log.info("[DepositService] COMPLETED depositCode={} userId={} amount={}",
                    depositCode, depositLog.getUserId(), depositLog.getAmount());
        } else {
            depositLog.setStatus(DepositStatus.FAILED);
            depositLog.setVnpResponseCode(result.getResponseCode());
            log.warn("[DepositService] FAILED depositCode={} responseCode={}", depositCode, result.getResponseCode());
        }

        depositLogRepository.save(depositLog);
        return VnpayIpnResponse.ok();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('WALLET_DEPOSIT')")
    public PageResponse<DepositLogResponse> getMyDeposits(
            Long userId, DepositStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        java.time.LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        java.time.LocalDateTime toDt   = to   != null ? to.atTime(LocalTime.MAX) : null;

        org.springframework.data.jpa.domain.Specification<DepositLog> spec =
            (root, query, cb) -> {
                java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                predicates.add(cb.equal(root.get("userId"), userId));
                if (status != null) predicates.add(cb.equal(root.get("status"), status));
                if (fromDt != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDt));
                if (toDt   != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDt));
                if (query != null) query.orderBy(cb.desc(root.get("createdAt")));
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };

        Page<DepositLogResponse> page = depositLogRepository.findAll(spec, pageable)
                .map(d -> toResponse(d, null));

        return PageResponse.<DepositLogResponse>builder()
                .items(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }

    private DepositLogResponse toResponse(DepositLog log, String paymentUrl) {
        return DepositLogResponse.builder()
                .id(log.getId())
                .depositCode(log.getDepositCode())
                .amount(log.getAmount())
                .status(log.getStatus())
                .paymentUrl(paymentUrl)
                .vnpTransactionNo(log.getVnpTransactionNo())
                .paidAt(log.getPaidAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
