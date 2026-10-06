package com.mkwang.backend.modules.wallet.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerItemResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerSummaryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantTransactionDetailResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantWalletTransactionResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalDetailResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingJournalItemResponse;
import com.mkwang.backend.modules.accounting.dto.response.AdvanceEmployeeSummaryResponse;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import com.mkwang.backend.modules.wallet.entity.ReferenceType;
import com.mkwang.backend.modules.wallet.entity.TransactionStatus;
import com.mkwang.backend.modules.wallet.entity.TransactionType;
import com.mkwang.backend.modules.wallet.service.AccountantLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/accountant/ledger")
@RequiredArgsConstructor
@Tag(name = "Accountant - Ledger", description = "Financial ledger for accountant review")
@SecurityRequirement(name = "bearerAuth")
public class AccountantLedgerController {

    private final AccountantLedgerService accountantLedgerService;

    @GetMapping("/wallet-transactions")
    @Operation(summary = "List wallet transactions grouped by transaction")
    public ResponseEntity<ApiResponse<PageResponse<AccountantWalletTransactionResponse>>> getWalletTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(accountantLedgerService.getWalletTransactions(
                type, status, referenceType, from, to, page, limit)));
    }

    @GetMapping("/journals")
    @Operation(summary = "List accounting journals")
    public ResponseEntity<ApiResponse<PageResponse<AccountingJournalItemResponse>>> getJournals(
            @RequestParam(required = false) AccountingJournalEvent event,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(accountantLedgerService.getJournals(event, from, to, page, limit)));
    }

    @GetMapping("/journals/{journalId}")
    @Operation(summary = "Get accounting journal detail")
    public ResponseEntity<ApiResponse<AccountingJournalDetailResponse>> getJournalDetail(@PathVariable Long journalId) {
        return ResponseEntity.ok(ApiResponse.success(accountantLedgerService.getJournalDetail(journalId)));
    }

    @GetMapping("/advances/outstanding")
    @Operation(summary = "List unsettled advances grouped by employee")
    public ResponseEntity<ApiResponse<List<AdvanceEmployeeSummaryResponse>>> getOutstandingAdvances() {
        return ResponseEntity.ok(ApiResponse.success(accountantLedgerService.getOutstandingAdvancesByEmployee()));
    }

    @GetMapping
    @Operation(summary = "Get transaction ledger",
               description = "Paginated list of all transactions. " +
                             "amount is signed from the CompanyFund perspective: " +
                             "negative = outflow (CF debited), positive = inflow (CF credited). " +
                             "Transactions not touching CompanyFund show unsigned amount.")
    public ResponseEntity<ApiResponse<PageResponse<AccountantLedgerItemResponse>>> getLedger(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                accountantLedgerService.getLedger(type, status, referenceType, from, to, page, limit)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get ledger summary",
               description = "CompanyFund balance snapshot plus inflow/outflow aggregates for the given date range. " +
                             "When from/to are omitted, all-time aggregates are returned.")
    public ResponseEntity<ApiResponse<AccountantLedgerSummaryResponse>> getLedgerSummary(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(
                accountantLedgerService.getLedgerSummary(type, status, referenceType, from, to)));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction detail",
               description = "Full detail of a single transaction including all double-entry ledger lines. " +
                             "Boundary transactions (SYSTEM_TOPUP, DEPOSIT, WITHDRAW) have only 1 ledger entry.")
    public ResponseEntity<ApiResponse<AccountantTransactionDetailResponse>> getTransactionDetail(
            @PathVariable Long transactionId) {

        return ResponseEntity.ok(ApiResponse.success(
                accountantLedgerService.getTransactionDetail(transactionId)));
    }
}
