package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.exception.InternalSystemException;
import com.mkwang.backend.modules.accounting.dto.response.CompanyFundResponse;
import com.mkwang.backend.modules.accounting.dto.response.ReconciliationReportResponse;
import com.mkwang.backend.modules.accounting.dto.request.SystemTopupRequest;
import com.mkwang.backend.modules.accounting.dto.request.UpdateBankStatementRequest;
import com.mkwang.backend.modules.accounting.entity.CompanyFund;
import com.mkwang.backend.modules.accounting.repository.CompanyFundRepository;
import com.mkwang.backend.modules.wallet.dto.response.TransactionResponse;
import com.mkwang.backend.modules.wallet.dto.response.WalletResponse;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.wallet.mapper.WalletMapper;
import com.mkwang.backend.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CompanyFundServiceImpl implements CompanyFundService {

    private final CompanyFundRepository companyFundRepository;
    private final WalletService         walletService;
    private final WalletMapper          walletMapper;
    private final AccountingJournalService accountingJournalService;
    private final com.mkwang.backend.modules.wallet.service.WalletFinancialEvidenceService financialEvidence;
    private final BankEvidenceService bankEvidence;

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('COMPANY_FUND_VIEW')")
    public CompanyFundResponse getCompanyFund() {
        CompanyFund fund = loadDefault();
        WalletResponse wallet = walletService.getWallet(WalletOwnerType.COMPANY_FUND, 1L);
        return toDto(fund, wallet);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_FUND_TOPUP')")
    public TransactionResponse topup(SystemTopupRequest request) {
        BankEvidenceService.TopupEvidence evidence = bankEvidence.requireBookedTopupEvidence(
                request.getBankEvidenceId(), request.getAmount());
        Transaction txn = walletService.systemTopup(
                request.getAmount(),
                evidence.bankReference(),
                request.getDescription()
        );
        accountingJournalService.recordSystemTopup(txn);
        bankEvidence.match(evidence.id(), txn.getId(), currentUserId(), "Nạp quỹ theo dòng sao kê BOOKED " + evidence.bankReference());
        return walletMapper.toTransactionResponse(txn);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_FUND_TOPUP')")
    public CompanyFundResponse updateBankStatement(UpdateBankStatementRequest request) {
        CompanyFund fund = loadDefault();
        if (!fund.isBankScopeConfirmed()) throw new com.mkwang.backend.common.exception.BadRequestException("Cần xác nhận phạm vi tài khoản tiền vận hành công ty trước khi đối soát");
        if(!request.getLastStatementDate().equals(request.getAsOf().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()))
            throw new com.mkwang.backend.common.exception.BadRequestException("Ngày sao kê phải khớp ngày tại thời điểm chốt số dư");
        fund.setStatementAsOf(request.getAsOf());
        fund.setStatementEvidenceRef(request.getEvidenceRef().trim());
        fund.setExternalBankBalance(request.getExternalBankBalance());
        fund.setLastStatementDate(request.getLastStatementDate());
        fund.setLastStatementUpdatedBy(currentUserId());
        companyFundRepository.save(fund);

        WalletResponse wallet = walletService.getWallet(WalletOwnerType.COMPANY_FUND, 1L);
        return toDto(fund, wallet);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('COMPANY_FUND_VIEW')")
    public ReconciliationReportResponse getReconciliationReport() {
        CompanyFund fund = loadDefault();
        WalletResponse companyFundWallet = walletService.getWallet(WalletOwnerType.COMPANY_FUND, 1L);
        WalletResponse floatMainWallet   = walletService.getWallet(WalletOwnerType.FLOAT_MAIN, 0L);

        BigDecimal floatMainBalance  = floatMainWallet.getBalance();
        BigDecimal computedWalletSum = walletService.sumAllBalancesExceptFloatMain();
        BigDecimal systemDiscrepancy = floatMainBalance.subtract(computedWalletSum);

        BigDecimal companyFundBalance   = companyFundWallet.getBalance();
        BigDecimal totalDeptWallets     = walletService.sumBalancesByType(WalletOwnerType.DEPARTMENT);
        BigDecimal totalProjectWallets  = walletService.sumBalancesByType(WalletOwnerType.PROJECT);
        BigDecimal totalUserWallets     = walletService.sumBalancesByType(WalletOwnerType.USER);

        BigDecimal externalBankBalance  = fund.getExternalBankBalance() != null
                ? fund.getExternalBankBalance() : BigDecimal.ZERO;
        boolean comparable = fund.isBankScopeConfirmed() && fund.getStatementAsOf() != null && fund.getStatementEvidenceRef() != null;
        BigDecimal corporate = comparable ? financialEvidence.corporateBalanceAt(fund.getStatementAsOf()) : null;
        BigDecimal pending = comparable ? bankEvidence.pendingSigned(fund.getStatementAsOf()) : null;
        BigDecimal bankDiscrepancy = comparable ? corporate.subtract(pending).subtract(externalBankBalance) : null;

        return ReconciliationReportResponse.builder()
                .generatedAt(LocalDateTime.now())
                // Integrity check
                .floatMainBalance(floatMainBalance)
                .computedWalletSum(computedWalletSum)
                .systemDiscrepancy(systemDiscrepancy)
                .systemIntegrityValid(systemDiscrepancy.compareTo(BigDecimal.ZERO) == 0)
                // Breakdown
                .companyFundBalance(companyFundBalance)
                .totalDeptWallets(totalDeptWallets)
                .totalProjectWallets(totalProjectWallets)
                .totalUserWallets(totalUserWallets)
                // Bank statement
                .externalBankBalance(externalBankBalance)
                .lastStatementDate(fund.getLastStatementDate())
                .bankDiscrepancy(bankDiscrepancy)
                .bankComparisonStatus(comparable ? "VERIFIED_SCOPE" : "MISSING_EVIDENCE")
                .corporateBalanceAtStatement(corporate).pendingCorporateAmount(pending)
                .statementAsOf(fund.getStatementAsOf()).statementEvidenceRef(fund.getStatementEvidenceRef())
                .build();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private CompanyFund loadDefault() {
        return companyFundRepository.findDefault()
                .orElseThrow(() -> new InternalSystemException("CompanyFund record not found — system not initialized"));
    }

    private CompanyFundResponse toDto(CompanyFund fund, WalletResponse wallet) {
        String updatedBy = null;
        if (fund.getLastStatementUpdatedBy() != null) {
            updatedBy = "userId:" + fund.getLastStatementUpdatedBy();
        }

        BigDecimal walletBalance  = wallet.getBalance();
        BigDecimal externalBal    = fund.getExternalBankBalance() != null
                ? fund.getExternalBankBalance() : BigDecimal.ZERO;

        boolean comparable = fund.isBankScopeConfirmed() && fund.getStatementAsOf() != null && fund.getStatementEvidenceRef() != null;
        BigDecimal corporate = comparable ? financialEvidence.corporateBalanceAt(fund.getStatementAsOf()) : null;
        BigDecimal pending = comparable ? bankEvidence.pendingSigned(fund.getStatementAsOf()) : null;
        return CompanyFundResponse.builder()
                .id(fund.getId())
                .bankName(fund.getBankName())
                .bankAccount(fund.getBankAccount())
                .currentWalletBalance(walletBalance)
                .externalBankBalance(externalBal)
                .bankDiscrepancy(comparable ? corporate.subtract(pending).subtract(externalBal) : null)
                .bankComparisonStatus(comparable ? "VERIFIED_SCOPE" : "MISSING_EVIDENCE")
                .corporateBalanceAtStatement(corporate).pendingCorporateAmount(pending)
                .statementAsOf(fund.getStatementAsOf()).statementEvidenceRef(fund.getStatementEvidenceRef())
                .bankScopeConfirmed(fund.isBankScopeConfirmed())
                .lastStatementDate(fund.getLastStatementDate())
                .lastStatementUpdatedBy(updatedBy)
                .build();
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.mkwang.backend.modules.auth.security.UserDetailsAdapter adapter) return adapter.getUser().getId();
        if (principal instanceof com.mkwang.backend.modules.user.entity.User user) {
            return user.getId();
        }
        return null;
    }
}
