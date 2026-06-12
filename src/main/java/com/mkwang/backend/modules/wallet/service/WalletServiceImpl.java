package com.mkwang.backend.modules.wallet.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.dto.SseEvent;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.sse.SseEventType;
import com.mkwang.backend.common.sse.SseService;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeGenerator;
import com.mkwang.backend.common.utils.businesscodegenerator.BusinessCodeType;
import com.mkwang.backend.modules.wallet.dto.response.LedgerEntryResponse;
import com.mkwang.backend.modules.wallet.dto.response.TransactionResponse;
import com.mkwang.backend.modules.wallet.dto.response.WalletResponse;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.wallet.mapper.WalletMapper;
import com.mkwang.backend.modules.wallet.repository.LedgerEntryRepository;
import com.mkwang.backend.modules.wallet.repository.TransactionRepository;
import com.mkwang.backend.modules.wallet.repository.WalletRepository;
import com.mkwang.backend.modules.wallet.service.locking.LockedWalletPair;
import com.mkwang.backend.modules.wallet.service.locking.WalletKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BusinessCodeGenerator codeGenerator;
    private final WalletMapper walletMapper;
    private final SseService sseService;

    // ══════════════════════════════════════════════════════════════════
    //  WRITE OPERATIONS
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Transaction transfer(WalletOwnerType fromType, Long fromId,
                                WalletOwnerType toType, Long toId,
                                BigDecimal amount, TransactionType txnType,
                                ReferenceType refType, Long refId,
                                String description) {
        validateAmount(amount);

        LockedWalletPair pair = lockPairDeterministically(fromType, fromId, toType, toId);
        Wallet source = pair.source();
        Wallet dest = pair.dest();

        source.debit(amount);
        dest.credit(amount);

        Transaction txn = buildTransaction(amount, txnType, refType, refId, description);
        txn.getEntries().add(LedgerEntry.debit(txn, source, amount, source.getBalance()));
        txn.getEntries().add(LedgerEntry.credit(txn, dest, amount, dest.getBalance()));

        walletRepository.save(source);
        walletRepository.save(dest);
        Transaction saved = transactionRepository.save(txn);
        pushWalletUpdate(fromType, fromId);
        pushWalletUpdate(toType, toId);
        pushTransactionHistoryUpdate(fromType, fromId, saved);
        pushTransactionHistoryUpdate(toType, toId, saved);
        return saved;
    }

    @Override
    @Transactional
    public void lockFunds(WalletOwnerType ownerType, Long ownerId, BigDecimal amount) {
        validateAmount(amount);
        Wallet wallet = getWalletForUpdate(ownerType, ownerId);
        wallet.lock(amount);
        walletRepository.save(wallet);
        pushWalletUpdate(ownerType, ownerId);
    }

    @Override
    @Transactional
    public void unlockFunds(WalletOwnerType ownerType, Long ownerId, BigDecimal amount) {
        validateAmount(amount);
        Wallet wallet = getWalletForUpdate(ownerType, ownerId);
        wallet.unlock(amount);
        walletRepository.save(wallet);
        pushWalletUpdate(ownerType, ownerId);
    }

    @Override
    @Transactional
    public Transaction settleAndTransfer(WalletOwnerType fromType, Long fromId,
                                         WalletOwnerType toType, Long toId,
                                         BigDecimal amount, TransactionType txnType,
                                         ReferenceType refType, Long refId,
                                         String description) {
        validateAmount(amount);

        LockedWalletPair pair = lockPairDeterministically(fromType, fromId, toType, toId);
        Wallet source = pair.source();
        Wallet dest = pair.dest();

        source.settle(amount);
        dest.credit(amount);

        Transaction txn = buildTransaction(amount, txnType, refType, refId, description);
        txn.getEntries().add(LedgerEntry.debit(txn, source, amount, source.getBalance()));
        txn.getEntries().add(LedgerEntry.credit(txn, dest, amount, dest.getBalance()));

        walletRepository.save(source);
        walletRepository.save(dest);
        Transaction saved = transactionRepository.save(txn);
        pushWalletUpdate(fromType, fromId);
        pushWalletUpdate(toType, toId);
        pushTransactionHistoryUpdate(fromType, fromId, saved);
        pushTransactionHistoryUpdate(toType, toId, saved);
        return saved;
    }

    @Override
    @Transactional
    public Transaction reversal(Long originalTransactionId, String reason) {
        Transaction original = transactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", originalTransactionId));

        if (original.getStatus() != TransactionStatus.SUCCESS) {
            throw new BadRequestException("Only SUCCESS transactions can be reversed");
        }

        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(originalTransactionId);
        BigDecimal amount = original.getAmount();
        String desc = "REVERSAL of " + original.getTransactionCode()
                + (reason != null ? " - " + reason : "");

        // ── Boundary transactions (single-entry: no counterpart wallet in IFMS) ──
        if (BOUNDARY_TYPES.contains(original.getType())) {
            if (originalEntries.size() != 1) {
                throw new BadRequestException("Boundary transaction should have exactly 1 ledger entry");
            }
            LedgerEntry entry = originalEntries.get(0);
            Wallet wallet = getWalletForUpdate(
                    entry.getWallet().getOwnerType(), entry.getWallet().getOwnerId());

            // Reverse the direction: CREDIT → DEBIT, DEBIT → CREDIT
            if (entry.getDirection() == TransactionDirection.CREDIT) {
                wallet.debit(amount);
            } else {
                wallet.credit(amount);
            }

            Transaction reversalTxn = buildTransaction(amount, TransactionType.REVERSAL,
                    original.getReferenceType(), original.getReferenceId(), desc);

            if (entry.getDirection() == TransactionDirection.CREDIT) {
                reversalTxn.getEntries().add(LedgerEntry.debit(reversalTxn, wallet, amount, wallet.getBalance()));
            } else {
                reversalTxn.getEntries().add(LedgerEntry.credit(reversalTxn, wallet, amount, wallet.getBalance()));
            }

            walletRepository.save(wallet);
            Transaction saved = transactionRepository.save(reversalTxn);

            // FLOAT_MAIN: reverse the boundary effect
            boolean wasInflow = entry.getDirection() == TransactionDirection.CREDIT;
            updateFloatMain(amount, !wasInflow);
            pushWalletUpdate(wallet.getOwnerType(), wallet.getOwnerId());
            pushTransactionHistoryUpdate(wallet.getOwnerType(), wallet.getOwnerId(), saved);
            return saved;
        }

        // ── Standard double-entry transactions ───────────────────────────────────
        if (originalEntries.size() != 2) {
            throw new BadRequestException("Transaction does not have exactly 2 ledger entries");
        }

        LedgerEntry debitEntry = originalEntries.stream()
                .filter(e -> e.getDirection() == TransactionDirection.DEBIT)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Original transaction missing DEBIT entry"));
        LedgerEntry creditEntry = originalEntries.stream()
                .filter(e -> e.getDirection() == TransactionDirection.CREDIT)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Original transaction missing CREDIT entry"));

        LockedWalletPair pair = lockPairDeterministically(
                debitEntry.getWallet().getOwnerType(), debitEntry.getWallet().getOwnerId(),
                creditEntry.getWallet().getOwnerType(), creditEntry.getWallet().getOwnerId()
        );
        Wallet originalSource = pair.source();
        Wallet originalDest = pair.dest();

        originalDest.debit(amount);
        originalSource.credit(amount);

        Transaction reversalTxn = buildTransaction(amount, TransactionType.REVERSAL,
                original.getReferenceType(), original.getReferenceId(), desc);

        reversalTxn.getEntries().add(LedgerEntry.debit(reversalTxn, originalDest, amount, originalDest.getBalance()));
        reversalTxn.getEntries().add(LedgerEntry.credit(reversalTxn, originalSource, amount, originalSource.getBalance()));

        walletRepository.save(originalSource);
        walletRepository.save(originalDest);
        Transaction saved = transactionRepository.save(reversalTxn);
        pushWalletUpdate(originalSource.getOwnerType(), originalSource.getOwnerId());
        pushWalletUpdate(originalDest.getOwnerType(), originalDest.getOwnerId());
        pushTransactionHistoryUpdate(originalSource.getOwnerType(), originalSource.getOwnerId(), saved);
        pushTransactionHistoryUpdate(originalDest.getOwnerType(), originalDest.getOwnerId(), saved);
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public WalletResponse createWallet(WalletOwnerType ownerType, Long ownerId) {
        if (walletRepository.existsByOwnerTypeAndOwnerId(ownerType, ownerId)) {
            throw new BadRequestException("Wallet already exists for " + ownerType + ":" + ownerId);
        }

        Wallet wallet = Wallet.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .build();
        WalletResponse created = walletMapper.toWalletResponse(walletRepository.save(wallet));
        pushWalletUpdate(ownerType, ownerId);
        return created;
    }

    // ══════════════════════════════════════════════════════════════════
    //  READ OPERATIONS
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(WalletOwnerType ownerType, Long ownerId) {
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner",
                        ownerType + ":" + ownerId));
        return walletMapper.toWalletResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getLedgerHistory(WalletOwnerType ownerType, Long ownerId,
                                                      LocalDate from, LocalDate to,
                                                      Pageable pageable) {
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner",
                        ownerType + ":" + ownerId));

        Page<LedgerEntry> entries;
        if (from != null && to != null) {
            entries = ledgerEntryRepository.findByWalletIdAndDateRange(
                    wallet.getId(),
                    from.atStartOfDay(),
                    to.atTime(LocalTime.MAX),
                    pageable);
        } else {
            entries = ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
        }

        return entries.map(walletMapper::toLedgerEntryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByReference(ReferenceType refType, Long refId) {
        return transactionRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(refType, refId)
                .stream()
                .map(walletMapper::toTransactionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('WALLET_VIEW_SELF')")
    public WalletResponse getMyWallet(Long userId) {
        return getWallet(WalletOwnerType.USER, userId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('WALLET_TRANSACTION_VIEW')")
    public PageResponse<LedgerEntryResponse> getMyTransactions(Long userId, LocalDate from, LocalDate to, Pageable pageable) {
        if ((from == null) != (to == null)) {
            throw new BadRequestException("Both 'from' and 'to' must be provided together");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' date must be before or equal to 'to' date");
        }

        Page<LedgerEntryResponse> page = getLedgerHistory(WalletOwnerType.USER, userId, from, to, pageable);
        return PageResponse.<LedgerEntryResponse>builder()
                .items(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('WALLET_TRANSACTION_VIEW')")
    public TransactionResponse getMyTransaction(Long userId, Long transactionId) {
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.USER, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner", "USER:" + userId));

        Transaction txn = transactionRepository.findOwnedTransactionById(transactionId, wallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        return walletMapper.toTransactionResponse(txn);
    }

    // ══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════

    private Wallet getWalletForUpdate(WalletOwnerType ownerType, Long ownerId) {
        return walletRepository.findByOwnerTypeAndOwnerIdForUpdate(ownerType, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner",
                        ownerType + ":" + ownerId));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
    }

    private Transaction buildTransaction(BigDecimal amount, TransactionType type,
                                          ReferenceType refType, Long refId,
                                          String description) {
        return Transaction.builder()
                .transactionCode(codeGenerator.generate(BusinessCodeType.TRANSACTION))
                .amount(amount)
                .type(type)
                .status(TransactionStatus.SUCCESS)
                .gatewayProvider(PaymentProvider.INTERNAL)
                .referenceType(refType)
                .referenceId(refId)
                .description(description)
                .build();
    }

    private LockedWalletPair lockPairDeterministically(
            WalletOwnerType fromType, Long fromId,
            WalletOwnerType toType, Long toId
    ) {
        WalletKey sourceKey = new WalletKey(fromType, fromId);
        WalletKey destKey = new WalletKey(toType, toId);

        WalletKey first = sourceKey.compareTo(destKey) <= 0 ? sourceKey : destKey;
        WalletKey second = sourceKey.compareTo(destKey) <= 0 ? destKey : sourceKey;

        Wallet firstLocked = getWalletForUpdate(first.ownerType(), first.ownerId());
        Wallet secondLocked = getWalletForUpdate(second.ownerType(), second.ownerId());

        Wallet source = sourceKey.equals(first) ? firstLocked : secondLocked;
        Wallet dest = sourceKey.equals(first) ? secondLocked : firstLocked;
        return new LockedWalletPair(source, dest);
    }

    // ══════════════════════════════════════════════════════════════════
    //  BOUNDARY OPERATIONS (External ↔ IFMS)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Transaction types that cross the system boundary (external ↔ IFMS).
     * These create only 1 LedgerEntry (no counterpart wallet inside IFMS)
     * and must update FLOAT_MAIN to maintain the system-wide invariant.
     */
    private static final Set<TransactionType> BOUNDARY_TYPES = Set.of(
            TransactionType.SYSTEM_TOPUP,
            TransactionType.DEPOSIT,
            TransactionType.WITHDRAW
    );

    @Override
    @Transactional
    public Transaction systemTopup(BigDecimal amount, String paymentRef, String description) {
        validateAmount(amount);

        Wallet companyFund = getWalletForUpdate(WalletOwnerType.COMPANY_FUND, 1L);
        companyFund.credit(amount);

        Transaction txn = Transaction.builder()
                .transactionCode(codeGenerator.generate(BusinessCodeType.TRANSACTION))
                .amount(amount)
                .type(TransactionType.SYSTEM_TOPUP)
                .status(TransactionStatus.SUCCESS)
                .gatewayProvider(PaymentProvider.INTERNAL)
                .referenceType(ReferenceType.SYSTEM)
                .referenceId(1L)
                .paymentRef(paymentRef)
                .description(description)
                .build();

        txn.getEntries().add(LedgerEntry.credit(txn, companyFund, amount, companyFund.getBalance()));

        walletRepository.save(companyFund);
        Transaction saved = transactionRepository.save(txn);

        // FLOAT_MAIN: money entered the system → credit
        updateFloatMain(amount, true);

        return saved;
    }

    @Override
    @Transactional
    public Transaction deposit(Long userId, BigDecimal amount, String paymentRef, Long depositRefId) {
        validateAmount(amount);
        Wallet userWallet = getWalletForUpdate(WalletOwnerType.USER, userId);
        userWallet.credit(amount);

        Transaction txn = Transaction.builder()
                .transactionCode(codeGenerator.generate(BusinessCodeType.TRANSACTION))
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .gatewayProvider(PaymentProvider.VNPAY)
                .referenceType(ReferenceType.DEPOSIT)
                .referenceId(depositRefId)
                .paymentRef(paymentRef)
                .description("Nap tien qua VNPay - " + paymentRef)
                .build();

        txn.getEntries().add(LedgerEntry.credit(txn, userWallet, amount, userWallet.getBalance()));
        walletRepository.save(userWallet);
        Transaction saved = transactionRepository.save(txn);

        // Money enters system → FLOAT_MAIN +
        updateFloatMain(amount, true);
        pushWalletUpdate(WalletOwnerType.USER, userId);
        pushTransactionHistoryUpdate(WalletOwnerType.USER, userId, saved);
        return saved;
    }

    @Override
    @Transactional
    public Transaction withdraw(Long userId, BigDecimal amount, String bankTxnId, Long withdrawReqId) {
        validateAmount(amount);
        Wallet userWallet = getWalletForUpdate(WalletOwnerType.USER, userId);
        // Funds were locked at PENDING time — settle() = unlock + debit
        userWallet.settle(amount);

        Transaction txn = Transaction.builder()
                .transactionCode(codeGenerator.generate(BusinessCodeType.TRANSACTION))
                .amount(amount)
                .type(TransactionType.WITHDRAW)
                .status(TransactionStatus.SUCCESS)
                .gatewayProvider(PaymentProvider.MOCK_BANK)
                .referenceType(ReferenceType.WITHDRAWAL)
                .referenceId(withdrawReqId)
                .paymentRef(bankTxnId)
                .description("Rut tien qua MockBank - " + bankTxnId)
                .build();

        txn.getEntries().add(LedgerEntry.debit(txn, userWallet, amount, userWallet.getBalance()));
        walletRepository.save(userWallet);
        Transaction saved = transactionRepository.save(txn);

        // Money leaves system → FLOAT_MAIN -
        updateFloatMain(amount, false);
        pushWalletUpdate(WalletOwnerType.USER, userId);
        pushTransactionHistoryUpdate(WalletOwnerType.USER, userId, saved);
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════
    //  RECONCILIATION READS
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumBalancesByType(WalletOwnerType ownerType) {
        return walletRepository.sumBalancesByType(ownerType);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumAllBalancesExceptFloatMain() {
        return walletRepository.sumAllBalancesExcept(WalletOwnerType.FLOAT_MAIN);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCompanyFundMonthlyInflow(int year, int month) {
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.COMPANY_FUND, 1L)
                .orElseThrow(() -> new com.mkwang.backend.common.exception.InternalSystemException("COMPANY_FUND wallet not found"));
        java.time.LocalDateTime from = java.time.LocalDate.of(year, month, 1).atStartOfDay();
        java.time.LocalDateTime to = from.plusMonths(1).minusNanos(1);
        return ledgerEntryRepository.sumCreditByWalletAndRange(wallet.getId(), from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCompanyFundMonthlyOutflow(int year, int month) {
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.COMPANY_FUND, 1L)
                .orElseThrow(() -> new com.mkwang.backend.common.exception.InternalSystemException("COMPANY_FUND wallet not found"));
        java.time.LocalDateTime from = java.time.LocalDate.of(year, month, 1).atStartOfDay();
        java.time.LocalDateTime to = from.plusMonths(1).minusNanos(1);
        return ledgerEntryRepository.sumDebitByWalletAndRange(wallet.getId(), from, to);
    }

    // ══════════════════════════════════════════════════════════════════
    //  SSE PUSH (private — best-effort)
    //  USER wallets  → sendToUser (general /stream emitter)
    //  Other wallets → sendToWalletSubscribers (dedicated wallet-stream emitters)
    // ══════════════════════════════════════════════════════════════════

    private void pushWalletUpdate(WalletOwnerType ownerType, Long ownerId) {
        if (ownerType == WalletOwnerType.FLOAT_MAIN) return;
        try {
            walletRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId).ifPresent(wallet -> {
                SseEvent event = SseEvent.builder()
                        .event(SseEventType.WALLET_UPDATED)
                        .data(walletMapper.toWalletResponse(wallet))
                        .build();
                if (ownerType == WalletOwnerType.USER) {
                    sseService.sendToUser(ownerId, event);
                } else {
                    sseService.sendToWalletSubscribers(walletKey(ownerType, ownerId), event);
                }
            });
        } catch (Exception e) {
            log.warn("[WalletService] SSE wallet push failed for {}:{}: {}", ownerType, ownerId, e.getMessage());
        }
    }

    private void pushTransactionHistoryUpdate(WalletOwnerType ownerType, Long ownerId, Transaction txn) {
        if (ownerType == WalletOwnerType.FLOAT_MAIN) return;
        try {
            LedgerEntry ownerEntry = txn.getEntries().stream()
                    .filter(entry -> entry.getWallet() != null
                            && entry.getWallet().getOwnerType() == ownerType
                            && ownerId.equals(entry.getWallet().getOwnerId()))
                    .findFirst()
                    .orElse(null);

            if (ownerEntry == null && txn.getId() != null) {
                ownerEntry = ledgerEntryRepository.findByTransactionId(txn.getId()).stream()
                        .filter(entry -> entry.getWallet() != null
                                && entry.getWallet().getOwnerType() == ownerType
                                && ownerId.equals(entry.getWallet().getOwnerId()))
                        .findFirst()
                        .orElse(null);
            }

            if (ownerEntry == null) {
                log.warn("[WalletService] No ledger entry found for transactionId={} {}:{}", txn.getId(), ownerType, ownerId);
                return;
            }

            SseEvent event = SseEvent.builder()
                    .event(SseEventType.TRANSACTION_CREATED)
                    .data(walletMapper.toLedgerEntryResponse(ownerEntry))
                    .build();
            if (ownerType == WalletOwnerType.USER) {
                sseService.sendToUser(ownerId, event);
            } else {
                sseService.sendToWalletSubscribers(walletKey(ownerType, ownerId), event);
            }
        } catch (Exception e) {
            log.warn("[WalletService] SSE transaction push failed for {}:{}: {}", ownerType, ownerId, e.getMessage());
        }
    }

    private static String walletKey(WalletOwnerType ownerType, Long ownerId) {
        return ownerType.name() + ":" + ownerId;
    }

    // ══════════════════════════════════════════════════════════════════
    //  FLOAT_MAIN MAINTENANCE (private — never called outside WalletService)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Update the FLOAT_MAIN control wallet when money crosses the system boundary.
     * FLOAT_MAIN does NOT get a LedgerEntry — it is a balance-only control wallet.
     *
     * @param amount   always positive
     * @param isCredit true when money enters the system (SYSTEM_TOPUP, DEPOSIT),
     *                 false when money exits (WITHDRAW)
     */
    private void updateFloatMain(BigDecimal amount, boolean isCredit) {
        Wallet floatMain = getWalletForUpdate(WalletOwnerType.FLOAT_MAIN, 0L);
        if (isCredit) {
            floatMain.credit(amount);
        } else {
            floatMain.debit(amount);
        }
        walletRepository.save(floatMain);
    }
}
