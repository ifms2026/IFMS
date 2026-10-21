package com.mkwang.backend.modules.accounting.dto.response;

import com.mkwang.backend.common.dto.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AccountLedgerResponse(LocalDate from, LocalDate to, Instant generatedAt, String coverage,
        List<AccountBalanceResponse> accounts, BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced,
        BigDecimal balanceBeforePage, PageResponse<AccountLedgerEntryResponse> entries) {
    // Signed balances always use debit minus credit; a negative value is a credit balance.
    public record AccountBalanceResponse(String accountCode, String accountName, String normalSide,
            BigDecimal openingBalance, BigDecimal debitAmount, BigDecimal creditAmount, BigDecimal closingBalance) {}
    public record AccountLedgerEntryResponse(Long journalId, String journalCode, LocalDate postingDate,
            String description, Long employeeId, Long projectId, String projectName,
            String phaseName, String categoryName, BigDecimal debitAmount, BigDecimal creditAmount) {}
}
