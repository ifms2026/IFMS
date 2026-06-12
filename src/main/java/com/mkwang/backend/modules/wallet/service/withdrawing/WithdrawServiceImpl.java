package com.mkwang.backend.modules.wallet.service.withdrawing;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.LockedException;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.UnauthorizedException;
import com.mkwang.backend.common.utils.PinValidator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.config.service.SystemConfigService;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.profile.dto.request.VerifyMyPinRequest;
import com.mkwang.backend.modules.profile.dto.response.PinVerifyResponse;
import com.mkwang.backend.modules.profile.service.ProfileService;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.repository.UserRepository;
import com.mkwang.backend.modules.wallet.dto.request.CreateWithdrawRequest;
import com.mkwang.backend.modules.wallet.dto.response.WithdrawRequestResponse;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.WithdrawRequest;
import com.mkwang.backend.modules.wallet.entity.WithdrawStatus;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.mapper.WithdrawMapper;
import com.mkwang.backend.modules.wallet.repository.WithdrawRequestRepository;
import com.mkwang.backend.modules.banking.dto.BankTransferResult;
import com.mkwang.backend.modules.banking.service.BankingService;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WithdrawServiceImpl — orchestrates the withdrawal lifecycle.
 *
 * Auto-execute logic (per-role limit from SystemConfig):
 *   amount <= WITHDRAW_LIMIT_{ROLE}  →  call MockBank immediately on createRequest()
 *   amount >  WITHDRAW_LIMIT_{ROLE}  →  create PENDING, accountant must run executeWithdraw()
 *
 * Permission scheme:
 *   WALLET_WITHDRAW              → user actions (create, cancel, view own)
 *   TRANSACTION_APPROVE_WITHDRAW → accountant actions (execute, reject, view all)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

    private final WithdrawRequestRepository withdrawRequestRepository;
    private final WalletService walletService;
    private final BankingService bankingService;
    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;
    private final BusinessCodeGenerator codeGenerator;
    private final WithdrawMapper withdrawMapper;
    private final PinValidator pinValidator;

    // ══════════════════════════════════════════════════════════════════
    //  USER ACTIONS
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, LockedException.class})
    @PreAuthorize("hasAuthority('WALLET_WITHDRAW')")
    public WithdrawRequestResponse createRequest(Long userId, CreateWithdrawRequest request) {
        if (!pinValidator.isValidFormat(request.pin())) {
            throw new BadRequestException("PIN phải gồm đúng " + pinValidator.getPinLength() + " chữ số");
        }
        PinVerifyResponse pinResult = profileService.verifyMyPin(userId, VerifyMyPinRequest.builder().pin(request.pin()).build());
        if (!pinResult.isValid()) {
            throw new UnauthorizedException("PIN không đúng");
        }

        // 1. Read bank info from UserProfile
        UserProfile profile = profileService.getProfileByUserId(userId);
        validateBankInfo(profile);

        // 2. Lock funds immediately
        walletService.lockFunds(WalletOwnerType.USER, userId, request.amount());

        // 3. Build the request (snapshot bank info)
        WithdrawRequest req = WithdrawRequest.builder()
                .withdrawCode(codeGenerator.generate(BusinessCodeType.WITHDRAWAL))
                .userId(userId)
                .amount(request.amount())
                .creditAccount(profile.getBankAccountNum())
                .creditAccountName(profile.getBankAccountOwner())
                .creditBankCode(deriveBankCode(profile.getBankName()))
                .creditBankName(profile.getBankName())
                .userNote(request.userNote())
                .status(WithdrawStatus.PENDING)
                .build();

        req = withdrawRequestRepository.save(req);
        log.info("[WithdrawService] Created id={} code={} userId={} amount={}",
                req.getId(), req.getWithdrawCode(), userId, request.amount());

        // 4. Check role limit — auto-execute if within limit
        BigDecimal roleLimit = getRoleWithdrawLimit(userId);
        if (request.amount().compareTo(roleLimit) <= 0) {
            log.info("[WithdrawService] amount={} <= roleLimit={} → auto-execute for userId={}",
                    request.amount(), roleLimit, userId);
            req = doExecuteWithdraw(req, null /* system */);
        }

        return withdrawMapper.toDto(req);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('WALLET_WITHDRAW')")
    public WithdrawRequestResponse cancelRequest(Long userId, Long requestId) {
        WithdrawRequest req = findById(requestId);

        if (!req.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bạn không có quyền hủy yêu cầu này");
        }
        if (req.getStatus() != WithdrawStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy yêu cầu ở trạng thái PENDING");
        }

        walletService.unlockFunds(WalletOwnerType.USER, userId, req.getAmount());
        req.setStatus(WithdrawStatus.CANCELLED);

        log.info("[WithdrawService] Cancelled id={} userId={}", requestId, userId);
        return withdrawMapper.toDto(withdrawRequestRepository.save(req));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('WALLET_WITHDRAW')")
    public PageResponse<WithdrawRequestResponse> getMyRequests(Long userId, Pageable pageable) {
        Page<WithdrawRequestResponse> page = withdrawRequestRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(withdrawMapper::toDto);

        return PageResponse.<WithdrawRequestResponse>builder()
                .items(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  ACCOUNTANT ACTIONS
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TRANSACTION_APPROVE_WITHDRAW')")
    public WithdrawRequestResponse executeWithdraw(Long accountantId, Long requestId, String note) {
        WithdrawRequest req = findById(requestId);

        if (req.getStatus() != WithdrawStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể execute yêu cầu ở trạng thái PENDING");
        }

        req.setAccountantNote(note);
        req = doExecuteWithdraw(req, accountantId);
        return withdrawMapper.toDto(req);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TRANSACTION_APPROVE_WITHDRAW')")
    public WithdrawRequestResponse rejectRequest(Long accountantId, Long requestId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lý do từ chối không được để trống");
        }
        WithdrawRequest req = findById(requestId);

        if (req.getStatus() != WithdrawStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể từ chối yêu cầu ở trạng thái PENDING");
        }

        walletService.unlockFunds(WalletOwnerType.USER, req.getUserId(), req.getAmount());
        req.setStatus(WithdrawStatus.REJECTED);
        req.setExecutedBy(accountantId);
        req.setExecutedAt(LocalDateTime.now());
        req.setAccountantNote(reason);

        log.info("[WithdrawService] REJECTED id={} accountantId={} reason={}", requestId, accountantId, reason);
        return withdrawMapper.toDto(withdrawRequestRepository.save(req));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRANSACTION_APPROVE_WITHDRAW')")
    public PageResponse<WithdrawRequestResponse> getAllRequests(WithdrawStatus status, Pageable pageable) {
        Page<WithdrawRequestResponse> page = (status != null)
                ? withdrawRequestRepository.findByStatusOrderByCreatedAtAsc(status, pageable).map(withdrawMapper::toDto)
                : withdrawRequestRepository.findAllByOrderByCreatedAtDesc(pageable).map(withdrawMapper::toDto);

        return PageResponse.<WithdrawRequestResponse>builder()
                .items(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  PRIVATE — CORE EXECUTE LOGIC (shared by auto-execute + accountant)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Core execution: call MockBank, update wallet + FLOAT_MAIN, update request status.
     * Used by both auto-execute (executedBy=null) and accountant executeWithdraw.
     *
     * @param req        The WithdrawRequest in PENDING state (already saved)
     * @param executedBy Accountant userId, or null if system auto-executed
     * @return The updated and saved WithdrawRequest
     */
    private WithdrawRequest doExecuteWithdraw(WithdrawRequest req, Long executedBy) {
        req.setExecutedBy(executedBy);
        req.setExecutedAt(LocalDateTime.now());

        log.info("[WithdrawService] Calling MockBank — ref={} amount={} executedBy={}",
                req.getWithdrawCode(), req.getAmount(), executedBy == null ? "SYSTEM" : executedBy);

        BankTransferResult result = bankingService.transfer(
                req.getCreditAccount(),
                req.getCreditAccountName(),
                req.getCreditBankCode(),
                req.getAmount(),
                req.getWithdrawCode(),
                "Chuyen khoan thu nhap - " + req.getWithdrawCode()
        );

        if (result.isSuccess() || result.isDuplicate()) {
            Transaction txn = walletService.withdraw(
                    req.getUserId(), req.getAmount(),
                    result.transactionId(), req.getId()
            );
            req.setStatus(WithdrawStatus.COMPLETED);
            req.setBankTransactionId(result.transactionId());
            req.setTransactionId(txn.getId());
            log.info("[WithdrawService] COMPLETED id={} bankTxnId={}", req.getId(), result.transactionId());
        } else {
            walletService.unlockFunds(WalletOwnerType.USER, req.getUserId(), req.getAmount());
            req.setStatus(WithdrawStatus.FAILED);
            req.setFailureReason("[" + result.responseCode() + "] " + result.responseMessage());
            log.warn("[WithdrawService] FAILED id={} reason={}", req.getId(), req.getFailureReason());
        }

        return withdrawRequestRepository.save(req);
    }

    // ══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════

    private void validateBankInfo(UserProfile profile) {
        if (profile.getBankAccountNum() == null || profile.getBankAccountNum().isBlank()) {
            throw new BadRequestException(
                "Vui lòng cập nhật số tài khoản ngân hàng trong hồ sơ cá nhân trước khi rút tiền");
        }
        if (profile.getBankAccountOwner() == null || profile.getBankAccountOwner().isBlank()) {
            throw new BadRequestException(
                "Vui lòng cập nhật họ tên chủ tài khoản ngân hàng trong hồ sơ cá nhân");
        }
        if (profile.getBankName() == null || profile.getBankName().isBlank()) {
            throw new BadRequestException(
                "Vui lòng cập nhật tên ngân hàng trong hồ sơ cá nhân");
        }
    }

    /**
     * Read withdrawal auto-approve limit for the user's role from SystemConfig (via Redis cache).
     * Config key: WITHDRAW_LIMIT_{ROLE_NAME}  e.g. WITHDRAW_LIMIT_EMPLOYEE
     * Falls back to 0 (= always require manual approval) if key not found.
     */
    private BigDecimal getRoleWithdrawLimit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        String configKey = "WITHDRAW_LIMIT_" + user.getRole().getName().toUpperCase();
        return systemConfigService.getAsBigDecimal(configKey, BigDecimal.ZERO);
    }

    private WithdrawRequest findById(Long requestId) {
        return withdrawRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("WithdrawRequest", "id", requestId));
    }

    /**
     * Derive a short bank code from the bank name stored in UserProfile.
     * Takes the first word, uppercased, capped at 10 chars.
     */
    private String deriveBankCode(String bankName) {
        if (bankName == null || bankName.isBlank()) return "UNKNOWN";
        String first = bankName.trim().split("\\s+")[0].toUpperCase();
        return first.length() > 10 ? first.substring(0, 10) : first;
    }
}
