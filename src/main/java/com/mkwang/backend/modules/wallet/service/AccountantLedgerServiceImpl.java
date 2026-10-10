package com.mkwang.backend.modules.wallet.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalItemResponse;
import com.mkwang.backend.modules.accounting.dto.response.AdvanceEmployeeSummaryResponse;
import com.mkwang.backend.modules.accounting.dto.response.LedgerProjectBudgetResponse;
import com.mkwang.backend.modules.accounting.entity.AccountingJournal;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import com.mkwang.backend.modules.accounting.repository.AccountingJournalRepository;
import com.mkwang.backend.modules.accounting.service.AccountingJournalService;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerEntryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerItemResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerSummaryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantTransactionDetailResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantWalletTransactionResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountantLedgerServiceImpl implements AccountantLedgerService {

    private static final WalletOwnerType CF_TYPE = WalletOwnerType.COMPANY_FUND;
    private static final Long CF_ID = 1L;

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final AccountingJournalRepository accountingJournalRepository;
    private final AccountingJournalService accountingJournalService;

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
    public AccountantLedgerSummaryResponse getLedgerSummary(TransactionType type, TransactionStatus status,
            ReferenceType referenceType, LocalDate from, LocalDate to) {
        validateFilters(from, to);
        Wallet cf = walletRepository.findByOwnerTypeAndOwnerId(CF_TYPE, CF_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "owner", "COMPANY_FUND:1"));

        Long cfWalletId = cf.getId();

        // When date range is absent use all-time aggregates
        LocalDateTime dtFrom = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime dtTo   = to   != null ? LocalDateTime.of(to, LocalTime.MAX) : LocalDateTime.now();

        BigDecimal totalInflow  = ledgerEntryRepository.sumCreditByWalletAndFilter(cfWalletId, dtFrom, dtTo, type, status, referenceType);
        BigDecimal totalOutflow = ledgerEntryRepository.sumDebitByWalletAndFilter(cfWalletId, dtFrom, dtTo, type, status, referenceType);
        long txCount            = ledgerEntryRepository.countTransactionsByWalletAndFilter(cfWalletId, dtFrom, dtTo, type, status, referenceType);

        return new AccountantLedgerSummaryResponse(
                cf.getBalance(),
                totalInflow,
                totalOutflow,
                txCount
        );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<AccountantWalletTransactionResponse> getWalletTransactions(
            TransactionType type, TransactionStatus status, ReferenceType referenceType,
            LocalDate from, LocalDate to, int page, int limit) {
        validatePage(from, to, page, limit);
        Page<Transaction> result = transactionRepository.findAll(
                com.mkwang.backend.modules.wallet.repository.TransactionSpecification.filter(type, status, referenceType, from, to),
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<Long> ids = result.getContent().stream().map(Transaction::getId).toList();
        Map<Long, List<AccountantLedgerEntryResponse>> movementsByTransaction = ids.isEmpty() ? Map.of()
                : ledgerEntryRepository.findByTransactionIdsWithWallet(ids).stream()
                        .collect(Collectors.groupingBy(entry -> entry.getTransaction().getId(), LinkedHashMap::new,
                                Collectors.mapping(walletMapper::toAccountantLedgerEntryResponse, Collectors.toList())));
        List<AccountantWalletTransactionResponse> items = result.getContent().stream().map(transaction ->
                new AccountantWalletTransactionResponse(transaction.getId(), transaction.getTransactionCode(),
                        transaction.getType(), transaction.getStatus(), transaction.getAmount(),
                        transaction.getReferenceType(), transaction.getReferenceId(), transaction.getDescription(),
                        transaction.getCreatedAt(), movementsByTransaction.getOrDefault(transaction.getId(), List.of())))
                .toList();
        return PageResponse.<AccountantWalletTransactionResponse>builder().items(items).total(result.getTotalElements())
                .page(page).size(limit).totalPages(result.getTotalPages()).build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PageResponse<AccountingJournalItemResponse> getJournals(AccountingJournalEvent event,
            LocalDate from, LocalDate to, Long employeeId, Long projectId, Long requestId, int page, int limit) {
        return accountingJournalService.getJournals(event, from, to, employeeId, projectId, requestId, page, limit);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountingJournalDetailResponse getJournalDetail(Long journalId) {
        return accountingJournalService.getJournalDetail(journalId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<AdvanceEmployeeSummaryResponse> getOutstandingAdvancesByEmployee() {
        return accountingJournalService.getOutstandingAdvancesByEmployee();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public List<LedgerProjectBudgetResponse> getBudgetExposure() {
        return accountingJournalService.getBudgetExposure();
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /accountant/ledger/:transactionId
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public AccountantTransactionDetailResponse getTransactionDetail(Long transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseGet(() -> ledgerEntryRepository.findById(transactionId)
                        .map(LedgerEntry::getTransaction)
                        .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId)));

        // Fetch all ledger entries with wallet eagerly loaded
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionIdWithWallet(txn.getId());

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
        String ownerName = walletMapper.resolveWalletOwnerName(ownerType, ownerId);

        LinkedHashSet<Long> journalIds = new LinkedHashSet<>();
        accountingJournalRepository.findByWalletTransactionIdOrderByIdAsc(txn.getId())
                .forEach(journal -> journalIds.add(journal.getId()));
        if (txn.getReferenceType() == ReferenceType.REQUEST && txn.getReferenceId() != null) {
            accountingJournalRepository.findByRequestIdOrderByIdAsc(txn.getReferenceId())
                    .forEach(journal -> journalIds.add(journal.getId()));
        }
        if (txn.getReferenceType() == ReferenceType.ADVANCE_BALANCE && txn.getReferenceId() != null) {
            accountingJournalRepository.findActivitiesForAdvance(txn.getReferenceId())
                    .forEach(journal -> journalIds.add(journal.getId()));
        }

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
                ownerName,
                txn.getDescription(),
                entryResponses,
                List.copyOf(journalIds),
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

    private void validatePage(LocalDate from, LocalDate to, int page, int limit) {
        validateFilters(from, to);
        if (page < 1 || limit < 1 || limit > 100) throw new BadRequestException("Invalid page or limit");
    }

    private void validateFilters(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new BadRequestException("from must be before or equal to to");
    }

}
