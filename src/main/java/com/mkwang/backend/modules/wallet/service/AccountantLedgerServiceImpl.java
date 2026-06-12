package com.mkwang.backend.modules.wallet.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerEntryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerItemResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerSummaryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantTransactionDetailResponse;
import com.mkwang.backend.modules.wallet.entity.LedgerEntry;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.TransactionDirection;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import com.mkwang.backend.modules.wallet.entity.Wallet;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.mapper.WalletMapper;
import com.mkwang.backend.modules.wallet.repository.LedgerEntryRepository;
import com.mkwang.backend.modules.wallet.repository.LedgerEntrySpecification;
import com.mkwang.backend.modules.wallet.repository.TransactionRepository;
import com.mkwang.backend.modules.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountantLedgerServiceImpl implements AccountantLedgerService {

    private static final WalletOwnerType CF_TYPE = WalletOwnerType.COMPANY_FUND;
    private static final Long CF_ID = 1L;

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/ledger
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<AccountantLedgerItemResponse> getLedger(
            TransactionType type, TransactionStatus status, ReferenceType referenceType,
            LocalDate from, LocalDate to, int page, int limit) {

        Specification<LedgerEntry> spec = LedgerEntrySpecification.filter(type, status, referenceType, from, to);
        Page<LedgerEntry> entryPage = ledgerEntryRepository.findAll(spec,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AccountantLedgerItemResponse> items = entryPage.getContent().stream()
                .map(walletMapper::toAccountantLedgerItemResponse)
                .toList();

        return PageResponse.<AccountantLedgerItemResponse>builder()
                .items(items)
                .total(entryPage.getTotalElements())
                .page(page)
                .size(limit)
                .totalPages(entryPage.getTotalPages())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/ledger/summary
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountantLedgerSummaryResponse getLedgerSummary(LocalDate from, LocalDate to) {
        Wallet cf = walletRepository.findByOwnerTypeAndOwnerId(CF_TYPE, CF_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner", "COMPANY_FUND:1"));

        Long cfWalletId = cf.getId();

        // When date range is absent use all-time aggregates
        LocalDateTime dtFrom = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime dtTo   = to   != null ? LocalDateTime.of(to, LocalTime.MAX) : LocalDateTime.now();

        BigDecimal totalInflow  = ledgerEntryRepository.sumCreditByWalletAndRange(cfWalletId, dtFrom, dtTo);
        BigDecimal totalOutflow = ledgerEntryRepository.sumDebitByWalletAndRange(cfWalletId, dtFrom, dtTo);
        long txCount            = ledgerEntryRepository.countTransactionsByWalletAndRange(cfWalletId, dtFrom, dtTo);

        return new AccountantLedgerSummaryResponse(
                cf.getBalance(),
                totalInflow,
                totalOutflow,
                txCount
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/ledger/:transactionId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountantTransactionDetailResponse getTransactionDetail(Long transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        // Fetch all ledger entries with wallet eagerly loaded
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionIdWithWallet(transactionId);

        List<AccountantLedgerEntryResponse> entryResponses = entries.stream()
                .map(walletMapper::toAccountantLedgerEntryResponse)
                .toList();

        // Signed amount from CompanyFund perspective
        Long cfWalletId = getCompanyFundWalletId();
        LedgerEntry cfEntry = entries.stream()
                .filter(e -> e.getWallet().getId().equals(cfWalletId))
                .findFirst().orElse(null);

        BigDecimal signedAmount;
        BigDecimal balanceAfter;
        if (cfEntry != null) {
            signedAmount = cfEntry.getDirection() == TransactionDirection.CREDIT
                    ? txn.getAmount() : txn.getAmount().negate();
            balanceAfter = cfEntry.getBalanceAfter();
        } else {
            signedAmount = txn.getAmount();
            balanceAfter = null;
        }

        // Primary wallet = DEBIT entry, falling back to first entry for single-entry boundary txns
        LedgerEntry primaryEntry = entries.stream()
                .filter(e -> e.getDirection() == TransactionDirection.DEBIT)
                .findFirst()
                .orElseGet(() -> entries.isEmpty() ? null : entries.get(0));

        WalletOwnerType ownerType = primaryEntry != null ? primaryEntry.getWallet().getOwnerType() : null;
        Long ownerId = primaryEntry != null ? primaryEntry.getWallet().getOwnerId() : null;

        return new AccountantTransactionDetailResponse(
                txn.getId(),
                txn.getTransactionCode(),
                txn.getPaymentRef(),
                txn.getGatewayProvider(),
                txn.getType(),
                txn.getStatus(),
                signedAmount,
                balanceAfter,
                txn.getReferenceType(),
                txn.getReferenceId(),
                ownerType,
                ownerId,
                txn.getDescription(),
                entryResponses,
                txn.getCreatedAt()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private Long getCompanyFundWalletId() {
        return walletRepository.findByOwnerTypeAndOwnerId(CF_TYPE, CF_ID)
                .map(Wallet::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner", "COMPANY_FUND:1"));
    }

}
